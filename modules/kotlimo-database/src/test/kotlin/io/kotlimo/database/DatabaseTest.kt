package io.kotlimo.database

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class User : Model() {
    override val table = "users"
    override val fillable = listOf("name", "email")
    override val timestamps = false

    companion object : ModelCompanion<User>(User::class)
}

class Post : Model() {
    override val table = "posts"
    override val fillable = listOf("user_id", "title")
    override val timestamps = false
}

class DatabaseTest {
    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        connection = Connection.connect("jdbc:h2:mem:kotlimo_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
        Model.connection = connection
        val schema = SchemaBuilder(connection)
        schema.drop("posts")
        schema.drop("users")
        schema.create("users") {
            id()
            string("name")
            string("email").unique()
        }
        schema.create("posts") {
            id()
            bigInteger("user_id")
            string("title")
        }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
        Model.connection = null
    }

    @Test
    fun `query builder compiles sql and bindings`() {
        val builder = connection.table("users")
            .select("id", "name")
            .where("email", "ada@example.com")
            .orderBy("id", "desc")
            .limit(5)
        assertEquals("SELECT id, name FROM users WHERE email = ? ORDER BY id DESC LIMIT 5", builder.toSql())
        assertEquals(listOf("ada@example.com"), builder.getBindings())
    }

    @Test
    fun `insert update delete and count`() {
        val id = connection.table("users").insert(mapOf("name" to "Ada", "email" to "ada@example.com"))
        assertTrue(id > 0)
        assertEquals(1, connection.table("users").count())
        connection.table("users").where("id", id).update(mapOf("name" to "Ada Lovelace"))
        assertEquals("Ada Lovelace", connection.table("users").find(id)?.get("name"))
        connection.table("users").where("id", id).delete()
        assertEquals(0, connection.table("users").count())
    }

    @Test
    fun `eloquent style model persistence`() {
        val user = User.create(mapOf("name" to "Grace", "email" to "grace@example.com"))
        assertNotNull(user.id)
        assertEquals("Grace", User.find(user.id!!)!!["name"])
        user["name"] = "Grace Hopper"
        user.save()
        assertEquals("Grace Hopper", User.findOrFail(user.id!!)["name"])
        assertEquals(1, User.count())
        user.delete()
        assertTrue(User.all().isEmpty())
    }

    @Test
    fun `relationships load related records`() {
        val user = User.create(mapOf("name" to "Alan", "email" to "alan@example.com"))
        connection.table("posts").insert(mapOf("user_id" to user.id, "title" to "On Computable Numbers"))
        val posts = user.hasMany(Post::class)
        assertEquals(1, posts.size)
        assertEquals("On Computable Numbers", posts.first()["title"])
        val owner = posts.first().belongsTo(User::class)
        assertEquals("Alan", owner?.get("name"))
    }

    @Test
    fun `findOrFail throws when missing`() {
        assertThrows(ModelNotFoundException::class.java) { User.findOrFail(999) }
    }

    @Test
    fun `where in and or where`() {
        connection.table("users").insert(mapOf("name" to "Ada", "email" to "ada@example.com"))
        connection.table("users").insert(mapOf("name" to "Alan", "email" to "alan@example.com"))
        val found = connection.table("users").whereIn("name", listOf("Ada", "Grace")).get()
        assertEquals(1, found.size)
        val orFound = connection.table("users").where("name", "Missing").orWhere("name", "Alan").get()
        assertEquals(1, orFound.size)
    }

    @Test
    fun `schema reports created tables`() {
        val schema = SchemaBuilder(connection)
        assertTrue(schema.hasTable("users"))
        schema.drop("users")
        assertFalse(schema.hasTable("users"))
    }

    @Test
    fun `migrator records ran migrations`() {
        val migrator = Migrator(connection)
        val migration = object : Migration() {
            override val name = "create_logs_table"
            override fun up(schema: SchemaBuilder) {
                schema.create("logs") {
                    id()
                    string("message")
                }
            }

            override fun down(schema: SchemaBuilder) {
                schema.drop("logs")
            }
        }
        migrator.migrate(listOf(migration))
        assertTrue(SchemaBuilder(connection).hasTable("logs"))
        assertTrue("create_logs_table" in migrator.ran())
        migrator.rollback(listOf(migration))
        assertFalse(SchemaBuilder(connection).hasTable("logs"))
    }
}
