package com.carvoice.agent.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a local development `.env` file without allowing it to override values
 * explicitly supplied by the operating system or deployment platform.
 */
object RuntimeEnvironment {
    fun load(
        systemEnvironment: Map<String, String> = System.getenv(),
        dotenvPath: Path = findDotenvPath(Path.of("").toAbsolutePath()),
    ): Map<String, String> {
        if (!Files.isRegularFile(dotenvPath)) return systemEnvironment

        val dotenvValues = Files.readAllLines(dotenvPath)
            .mapNotNull(::parseLine)
            .toMap()

        return dotenvValues + systemEnvironment
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val content = line.trim()
        if (content.isEmpty() || content.startsWith('#')) return null

        val separator = content.indexOf('=')
        if (separator <= 0) return null

        val key = content.substring(0, separator).trim().removePrefix("export ").trim()
        val rawValue = content.substring(separator + 1).trim()
        if (key.isEmpty()) return null

        return key to rawValue.removeMatchingQuotes()
    }

    private fun String.removeMatchingQuotes(): String =
        if (length >= 2 && first() == last() && first() in setOf('\'', '"')) {
            substring(1, lastIndex)
        } else {
            this
        }

    internal fun findDotenvPath(workingDirectory: Path): Path {
        return generateSequence(workingDirectory) { it.parent }
            .map { it.resolve(".env") }
            .firstOrNull(Files::isRegularFile)
            ?: workingDirectory.resolve(".env")
    }
}
