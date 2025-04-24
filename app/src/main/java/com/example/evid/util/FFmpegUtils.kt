package com.example.evid.util


fun isAudioCommand(command: String): Boolean {
    return command.contains("-map a", ignoreCase = true)
}

fun sanitizeCommand(command: String): String {
    return command.trim()
}