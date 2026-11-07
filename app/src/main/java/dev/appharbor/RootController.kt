package dev.appharbor

import java.util.concurrent.TimeUnit

data class RootResult(val success: Boolean, val output: String)

object RootController {
    private val safePackage = Regex("^[A-Za-z0-9_.]+$")

    fun safePackages(packages: Collection<String>): List<String> =
        packages.filter { safePackage.matches(it) }.distinct()

    fun run(command: String): RootResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val outputBuffer = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().use { outputBuffer.append(it.readText()) }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(40, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(3, TimeUnit.SECONDS)
                reader.join(1500)
                RootResult(false, "Root command timed out.")
            } else {
                reader.join(1500)
                val status = process.exitValue()
                val output = outputBuffer.toString().trim()
                RootResult(status == 0, output.ifBlank { if (status == 0) "Done." else "Command failed ($status)." })
            }
        } catch (e: Exception) {
            RootResult(false, e.message ?: "Root access is not available.")
        }
    }
}
