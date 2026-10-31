package com.nihaltp.sbskip.util.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FfprobeTagValue(val tags: Map<String, String>?)

@Serializable
private data class FfprobeFormat(val format: FfprobeTagValue?)

object SbskipFfprobeJson {
    val json = Json { ignoreUnknownKeys = true }

    fun parse(output: String): FfprobeFormat {
        return json.decodeFromString(output)
    }
}
