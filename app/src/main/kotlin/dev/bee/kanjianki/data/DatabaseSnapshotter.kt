package dev.bee.kanjianki.data

import java.io.File
import java.io.IOException

interface DatabaseSnapshotter {
    @Throws(IOException::class)
    fun snapshotInto(destination: File)
}
