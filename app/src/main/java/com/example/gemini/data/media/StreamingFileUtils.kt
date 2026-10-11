package com.example.gemini.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * High-performance, zero-memory streaming file utilities.
 * Supports unlimited GB uploads and streaming without triggering OutOfMemoryError.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 */
object StreamingFileUtils {
    private const val TAG = "StreamingFileUtils"
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk buffer

    /**
     * Streams content from a [Uri] directly into [destFile] in 64 KB chunks.
     * Memory usage remains fixed at 64 KB regardless of whether the file is
     * 100 MB, 1 GB, 10 GB, or unlimited GB.
     *
     * @return Total bytes written, or -1L on failure.
     */
    fun streamUriToFile(
        context: Context,
        uri: Uri,
        destFile: File,
        onProgress: ((bytesCopied: Long) -> Unit)? = null
    ): Long {
        destFile.parentFile?.mkdirs()
        var totalBytes = 0L
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        onProgress?.invoke(totalBytes)
                    }
                    output.flush()
                }
            } ?: -1L
            totalBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed streaming uri $uri to ${destFile.absolutePath}", e)
            -1L
        }
    }

    /**
     * Safely generates a low-memory thumbnail as a Base64 string for image files.
     * Uses [BitmapFactory.Options.inJustDecodeBounds] to avoid decoding full-resolution
     * multi-megapixel / multi-gigabyte images into RAM.
     */
    fun getSafeDownsampledImageBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 512
    ): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            val outWidth = boundsOptions.outWidth
            val outHeight = boundsOptions.outHeight
            if (outWidth <= 0 || outHeight <= 0) return null

            var sampleSize = 1
            val maxOut = max(outWidth, outHeight)
            while (maxOut / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                bitmap.recycle()
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM in getSafeDownsampledImageBase64", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating downsampled thumbnail", e)
            null
        }
    }

    /**
     * Formats byte size into human-readable representation (B, KB, MB, GB, TB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        if (gb < 1024) return "%.2f GB".format(gb)
        val tb = gb / 1024.0
        return "%.2f TB".format(tb)
    }
}
