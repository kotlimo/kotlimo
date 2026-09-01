package io.kotlimo.console

import io.kotlimo.database.MigrationLoader
import io.kotlimo.database.Migrator
import io.kotlimo.queue.QueueManager
import io.kotlimo.scheduling.Schedule
import io.kotlimo.support.Inflector
import io.kotlimo.support.Str
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MakeMigrationCommand : Command() {
    override val signature = "make:migration {name}"
    override val description = "Create a new JSON migration file"

    override fun handle(): Int {
        val name = argument("name") ?: run {
            error("Migration name is required")
            return 1
        }
        val directory = app.databasePath().resolve("migrations")
        Files.createDirectories(directory)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"))
        val slug = Str.snake(name)
        val file = directory.resolve("${stamp}_$slug.json")
        val table = inferTable(slug)
        val content = """
            {
              "name": "${stamp}_$slug",
              "up": {
                "create": "$table",
                "columns": [
                  {"name": "id", "type": "id"},
                  {"name": "timestamps", "type": "timestamps"}
                ]
              },
              "down": {
                "drop": "$table"
              }
            }
        """.trimIndent() + "\n"
        Files.writeString(file, content)
        info("Migration created: $file")
        return 0
    }

    private fun inferTable(slug: String): String {
        val match = Regex("create_(.+)_table").find(slug)
        return match?.groupValues?.get(1) ?: Inflector.tableize(slug)
    }
}

class MigrateCommand : Command() {
    override val signature = "migrate"
    override val description = "Run outstanding database migrations"

    override fun handle(): Int {
        val migrator = app.make(Migrator::class)
        val migrations = MigrationLoader.fromDirectory(app.databasePath().resolve("migrations"))
        val ran = migrator.migrate(migrations)
        if (ran.isEmpty()) {
            info("Nothing to migrate.")
        } else {
            ran.forEach { info("Migrated: $it") }
        }
        return 0
    }
}

class MigrateRollbackCommand : Command() {
    override val signature = "migrate:rollback {--step=1}"
    override val description = "Rollback the last database migration batch"

    override fun handle(): Int {
        val step = option("step", "1")?.toIntOrNull() ?: 1
        val migrator = app.make(Migrator::class)
        val migrations = MigrationLoader.fromDirectory(app.databasePath().resolve("migrations"))
        val rolled = migrator.rollback(migrations, step)
        if (rolled.isEmpty()) {
            info("Nothing to rollback.")
        } else {
            rolled.forEach { info("Rolled back: $it") }
        }
        return 0
    }
}

class KeyGenerateCommand : Command() {
    override val signature = "key:generate"
    override val description = "Set the application key in .env"

    override fun handle(): Int {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val key = "base64:" + java.util.Base64.getEncoder().encodeToString(bytes)
        val envFile = app.basePath.resolve(".env")
        if (Files.exists(envFile)) {
            val updated = Files.readString(envFile).lines().joinToString("\n") { line ->
                if (line.startsWith("APP_KEY=")) "APP_KEY=$key" else line
            }.let { body ->
                if (body.contains("APP_KEY=")) body else body.trimEnd() + "\nAPP_KEY=$key\n"
            }
            Files.writeString(envFile, if (updated.endsWith("\n")) updated else "$updated\n")
        } else {
            Files.writeString(envFile, "APP_KEY=$key\n")
        }
        info("Application key set successfully.")
        return 0
    }
}

class ScheduleRunCommand : Command() {
    override val signature = "schedule:run"
    override val description = "Run the scheduled tasks that are due"

    override fun handle(): Int {
        val ran = app.make(Schedule::class).runDue()
        info("Ran $ran scheduled event(s).")
        return 0
    }
}

class QueueWorkCommand : Command() {
    override val signature = "queue:work {--once=false}"
    override val description = "Process jobs on the array queue (sync driver runs jobs immediately)"

    override fun handle(): Int {
        val manager = app.make(QueueManager::class)
        val once = option("once", "false") == "true"
        var processed = 0
        while (true) {
            val job = manager.pop() ?: break
            job.handle()
            processed++
            if (once) break
        }
        info("Processed $processed job(s).")
        return 0
    }
}
