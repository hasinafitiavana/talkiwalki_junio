package com.talkiwalki.junior.network

import java.net.URI

/** Compact QR format used to share a local group invitation. */
object QrGroupInvitation {
    private const val PREFIX = "talkiwalki-junior://join"

    fun create(address: String, port: Int): String = "$PREFIX?host=$address&port=$port"

    fun parse(value: String): String? = runCatching {
        val uri = URI(value.trim())
        if (uri.scheme != "talkiwalki-junior" || uri.host != "join") return null
        val parameters = uri.rawQuery.orEmpty().split('&').associate { parameter ->
            val (key, parameterValue) = parameter.split('=', limit = 2).let { it.firstOrNull().orEmpty() to it.getOrNull(1).orEmpty() }
            key to java.net.URLDecoder.decode(parameterValue, Charsets.UTF_8.name())
        }
        val host = parameters["host"]?.trim().orEmpty()
        val port = parameters["port"]?.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) null
        else if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }.getOrNull()
}
