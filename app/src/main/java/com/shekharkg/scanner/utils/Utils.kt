package com.shekharkg.scanner.utils

import android.content.Context
import android.hardware.camera2.CaptureResult
import android.media.Image
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class CombinedCaptureResult(
    val image: Image,
    val metadata: CaptureResult,
    val orientation: Int,
    val format: Int
) : Closeable {
    override fun close() = image.close()
}

private fun createFile(context: Context, extension: String): File {
    val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
    return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
}