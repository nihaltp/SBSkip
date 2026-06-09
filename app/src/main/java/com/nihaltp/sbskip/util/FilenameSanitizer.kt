package com.nihaltp.sbskip.util

object FilenameSanitizer {
    private val invalidCharacters = Regex("[\\\\/:*?\"<>|]")

    fun sanitize(
        name: String,
        replacement: Char = '_',
    ): String {
        val normalized = name.trim().replace(invalidCharacters, replacement.toString())
        return normalized.replace(Regex("\\s+"), " ").ifBlank { "sb_skip_download" }
    }
}
