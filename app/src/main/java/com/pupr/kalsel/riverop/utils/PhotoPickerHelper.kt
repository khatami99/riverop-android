package com.pupr.kalsel.riverop.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoPickerHelper(private val context: Context) {

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    fun getContentUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun compressAndSaveImage(sourceUri: Uri, targetFile: File): String? {
        return try {
            val maxDimension = 1280

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            var inSampleSize = 1
            while (boundsOptions.outWidth / inSampleSize > maxDimension * 2 ||
                boundsOptions.outHeight / inSampleSize > maxDimension * 2
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            var bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val rotation = getExifRotation(sourceUri)
            val matrix = Matrix()
            if (rotation != 0) matrix.postRotate(rotation.toFloat())
            val width = bitmap.width
            val height = bitmap.height
            val needsScale = width > maxDimension || height > maxDimension
            if (needsScale) {
                val scale = maxDimension.toFloat() / maxOf(width, height)
                matrix.postScale(scale, scale)
            }
            if (rotation != 0 || needsScale) {
                val original = bitmap
                bitmap = Bitmap.createBitmap(original, 0, 0, width, height, matrix, true)
                original.recycle()
            }

            var quality = 90
            var outputStream = FileOutputStream(targetFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.close()

            while (targetFile.length() > 300 * 1024 && quality > 20) {
                quality -= 10
                outputStream = FileOutputStream(targetFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.close()
            }

            bitmap.recycle()
            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifRotation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}