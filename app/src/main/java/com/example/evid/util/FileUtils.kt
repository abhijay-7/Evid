package com.example.evid.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

fun getOriginalFilename(context: Context, uri: Uri): String? {
    var filename: String? = null
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                filename = it.getString(nameIndex)
            }
        }
    }
    return filename
}

fun prepareFilename(userInput: String, defaultName: String, inputUri: Uri?, isAudio: Boolean, context: Context): String {
    val sanitized = userInput.trim().replace("[^a-zA-Z0-9.]".toRegex(), "_")
    val extension = if (isAudio) ".mp3" else ".mp4"
    return when {
        sanitized.isNotEmpty() -> {
            if (sanitized.endsWith(extension, ignoreCase = true)) sanitized else "$sanitized$extension"
        }
        inputUri != null -> {
            val original = getOriginalFilename(context, inputUri)
            if (original != null && original.endsWith(".mp4", ignoreCase = true)) {
                if (isAudio) original.replace(".mp4", ".mp3", ignoreCase = true) else original
            } else {
                defaultName
            }
        }
        else -> defaultName
    }
}
