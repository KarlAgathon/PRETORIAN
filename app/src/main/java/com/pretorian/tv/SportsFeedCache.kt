package com.pretorian.tv

import java.io.File
import java.io.IOException

/** One validated provider response per league and date range. Readers only see complete files. */
internal object SportsFeedCache {
    fun fresh(file: File?, now: Long, lifetime: Long): Boolean =
        file != null && file.isFile && now - file.lastModified() in 0 until lifetime

    fun save(file: File, response: String) {
        val directory = file.parentFile ?: return
        directory.mkdirs()
        val temp = File.createTempFile("board", ".tmp", directory)
        try {
            temp.writeText(response)
            if (!temp.renameTo(file)) throw IOException("Scoreboard cache replacement failed")
        } finally {
            temp.delete()
        }
    }
}
