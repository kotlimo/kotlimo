package io.kotlimo.foundation.providers

import io.kotlimo.config.ConfigRepository
import io.kotlimo.config.Environment
import io.kotlimo.database.Connection
import io.kotlimo.database.DatabaseManager
import io.kotlimo.database.Model
import io.kotlimo.database.SchemaBuilder
import io.kotlimo.foundation.Application
import io.kotlimo.foundation.ServiceProvider

class DatabaseServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.singleton(DatabaseManager::class) { container ->
            val config = container.make(ConfigRepository::class)
            val env = container.make(Environment::class)
            val manager = DatabaseManager()
            val url = env.get("DB_URL")
                ?: config.string("database.url").ifBlank { "jdbc:h2:mem:kotlimo;MODE=MySQL;DB_CLOSE_DELAY=-1" }
            val username = env.get("DB_USERNAME") ?: config.string("database.username")
            val password = env.get("DB_PASSWORD") ?: config.string("database.password")
            manager.addConnection("default", Connection.connect(url, username, password))
            manager
        }
    }

    override fun boot() {
        val manager = app.make(DatabaseManager::class)
        Model.connection = manager.connection()
        app.instance(Connection::class, manager.connection())
        app.bind(SchemaBuilder::class) { SchemaBuilder(manager.connection()) }
    }
}
