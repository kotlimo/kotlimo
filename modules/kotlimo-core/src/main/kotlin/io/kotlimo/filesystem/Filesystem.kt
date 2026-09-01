package io.kotlimo.filesystem

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

interface Filesystem {
    fun put(path: String, contents: String): Boolean
    fun put(path: String, bytes: ByteArray): Boolean
    fun get(path: String): String
    fun getBytes(path: String): ByteArray
    fun exists(path: String): Boolean
    fun delete(path: String): Boolean
    fun missing(path: String): Boolean = !exists(path)
}

class LocalFilesystem(private val root: Path) : Filesystem {
    init {
        Files.createDirectories(root)
    }

    override fun put(path: String, contents: String): Boolean =
        put(path, contents.toByteArray(Charsets.UTF_8))

    override fun put(path: String, bytes: ByteArray): Boolean {
        val target = resolve(path)
        Files.createDirectories(target.parent)
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return true
    }

    override fun get(path: String): String = getBytes(path).toString(Charsets.UTF_8)

    override fun getBytes(path: String): ByteArray {
        val target = resolve(path)
        if (!Files.isRegularFile(target)) {
            throw java.nio.file.NoSuchFileException(target.toString())
        }
        return Files.readAllBytes(target)
    }

    override fun exists(path: String): Boolean = Files.isRegularFile(resolve(path))

    override fun delete(path: String): Boolean = Files.deleteIfExists(resolve(path))

    private fun resolve(path: String): Path {
        val cleaned = path.trim().removePrefix("/").replace('\\', '/')
        val target = root.resolve(cleaned).normalize()
        if (!target.startsWith(root.normalize())) {
            throw SecurityException("Path [$path] is outside the disk root")
        }
        return target
    }
}

class FilesystemManager(private val disks: MutableMap<String, Filesystem> = mutableMapOf()) {
    var default: String = "local"

    fun add(name: String, filesystem: Filesystem) {
        disks[name] = filesystem
        if (disks.size == 1) default = name
    }

    fun disk(name: String? = null): Filesystem =
        disks[name ?: default] ?: throw IllegalStateException("Disk [${name ?: default}] is not configured")
}
