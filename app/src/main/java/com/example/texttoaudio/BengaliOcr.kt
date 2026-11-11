package com.example.texttoaudio

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Offline OCR for Bengali and English text in images and scanned PDF pages. */
class BengaliOcr(context: Context) : Closeable {
    private val api = TessBaseAPI()

    init {
        val dataRoot = File(context.filesDir, "tesseract")
        val tessdata = File(dataRoot, "tessdata")
        if (!tessdata.exists() && !tessdata.mkdirs()) {
            throw IllegalStateException("Could not prepare OCR language data")
        }

        copyModelIfNeeded(context, tessdata, "ben")
        copyModelIfNeeded(context, tessdata, "eng")

        if (!api.init(dataRoot.absolutePath, "ben+eng")) {
            api.recycle()
            throw IllegalStateException("Could not initialize Bengali OCR")
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
    }

    fun recognize(resolver: ContentResolver, uri: Uri): String {
        val bitmap = decodeBitmap(resolver, uri)
        return try {
            recognize(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun recognize(bitmap: Bitmap): String {
        api.setImage(bitmap)
        return api.getUTF8Text().orEmpty().trim()
    }

    override fun close() {
        api.recycle()
    }

    private fun copyModelIfNeeded(context: Context, tessdata: File, language: String) {
        val target = File(tessdata, "$language.traineddata")
        if (target.isFile && target.length() > 0) return

        context.assets.open("tessdata/$language.traineddata").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        // Some gallery and cloud-photo providers expose one-shot or short-lived streams.
        // Stage the image once so bounds, pixels, and EXIF all read the same content.
        val stagedImage = File.createTempFile("ocr-image-", ".img")
        try {
            val input = try {
                resolver.openInputStream(uri)
            } catch (error: Exception) {
                throw IOException("The selected image could not be opened. Choose another photo and try again.", error)
            } ?: throw IOException("The selected image could not be opened. Choose another photo and try again.")
            input.use { source ->
                stagedImage.outputStream().use { destination -> source.copyTo(destination) }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(stagedImage.absolutePath, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("This image format is not supported. Try a JPG or PNG photo.")
            }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(stagedImage.absolutePath, options)
                ?: throw IOException("This image could not be decoded. Try another photo.")

            val rotation = runCatching {
                ExifInterface(stagedImage.absolutePath).getRotationDegrees()
            }.getOrDefault(0)
            if (rotation == 0) return bitmap

            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        } finally {
            stagedImage.delete()
        }
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION = 2500
    }
}
