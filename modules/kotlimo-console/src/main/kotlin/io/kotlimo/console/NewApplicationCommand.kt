package io.kotlimo.console

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NewApplicationCommand {
    fun handle(args: List<String>): Int {
        val name = args.firstOrNull { !it.startsWith("--") }
        if (name.isNullOrBlank()) {
            System.err.println("Application name is required.")
            System.err.println("Usage: ./gradlew :kotlimo-console:run --args='new blog'")
            return 1
        }
        val pathOption = args.firstOrNull { it.startsWith("--path=") }?.substringAfter("=")
        val cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val target = (if (pathOption.isNullOrBlank()) cwd.resolve(name) else Path.of(pathOption).resolve(name))
            .toAbsolutePath().normalize()
        if (Files.exists(target) && Files.list(target).use { it.findFirst().isPresent }) {
            System.err.println("Directory already exists and is not empty: $target")
            return 1
        }
        Files.createDirectories(target)
        val kotlimoHome = detectKotlimoHome()
        val includeBuild = kotlimoHome?.let { "includeBuild(\"${escape(it.toString())}\")" } ?: ""
        AppSkeleton.files(name, includeBuild).forEach { (relative, contents) ->
            val file = target.resolve(relative)
            Files.createDirectories(file.parent)
            Files.writeString(file, contents)
        }
        kotlimoHome?.let { copyWrapper(it, target) }
        println("Kotlimo application created at $target")
        println("")
        println("Next steps:")
        println("  cd ${target.fileName}")
        println("  ./gradlew run")
        return 0
    }

    private fun copyWrapper(kotlimoHome: Path, target: Path) {
        val wrapper = kotlimoHome.resolve("gradle/wrapper")
        if (!Files.isDirectory(wrapper)) return
        val dest = target.resolve("gradle/wrapper")
        Files.createDirectories(dest)
        listOf("gradle-wrapper.jar", "gradle-wrapper.properties").forEach { name ->
            val source = wrapper.resolve(name)
            if (Files.exists(source)) {
                Files.copy(source, dest.resolve(name), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        listOf("gradlew", "gradlew.bat").forEach { name ->
            val source = kotlimoHome.resolve(name)
            if (Files.exists(source)) {
                Files.copy(source, target.resolve(name), StandardCopyOption.REPLACE_EXISTING)
                target.resolve(name).toFile().setExecutable(true)
            }
        }
    }

    companion object {
        fun detectKotlimoHome(): Path? {
            System.getenv("KOTLIMO_HOME")?.takeIf { it.isNotBlank() }?.let { return Path.of(it).toAbsolutePath().normalize() }
            var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
            while (dir != null) {
                val settings = dir.resolve("settings.gradle.kts")
                if (Files.isRegularFile(settings) && Files.readString(settings).contains("rootProject.name = \"kotlimo\"")) {
                    return dir
                }
                dir = dir.parent
            }
            return null
        }

        private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
