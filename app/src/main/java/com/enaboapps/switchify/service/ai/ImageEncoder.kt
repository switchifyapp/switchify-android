package com.enaboapps.switchify.service.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Utility class for encoding images to base64 format for AI processing
 */
object ImageEncoder {
    private const val TAG = "ImageEncoder"
    private const val DEFAULT_QUALITY = 85
    private const val MAX_DIMENSION = 1024

    /**
     * Encodes a bitmap to base64 string for AI processing
     * @param bitmap The bitmap to encode
     * @param quality JPEG compression quality (0-100)
     * @param maxDimension Maximum width/height, image will be scaled down if larger
     * @return Base64 encoded string or null if encoding fails
     */
    fun encodeToBase64(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        maxDimension: Int = MAX_DIMENSION
    ): String? {
        return try {
            val processedBitmap = resizeBitmapIfNeeded(bitmap, maxDimension)
            val outputStream = ByteArrayOutputStream()
            
            val compressionResult = processedBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                quality,
                outputStream
            )
            
            if (!compressionResult) {
                Log.e(TAG, "Failed to compress bitmap")
                return null
            }
            
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            
            Log.d(TAG, "Image encoded successfully, size: ${byteArray.size} bytes")
            base64String
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap to base64", e)
            null
        }
    }

    /**
     * Resizes bitmap if it exceeds the maximum dimension while maintaining aspect ratio
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / aspectRatio).toInt()
        } else {
            (maxDimension * aspectRatio).toInt() to maxDimension
        }
        
        Log.d(TAG, "Resizing bitmap from ${width}x${height} to ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Creates a multimodal part for Firebase AI from a bitmap
     * @param bitmap The image to encode
     * @param mimeType The MIME type (default: "image/jpeg")
     * @return EncodedImage object for AI processing
     */
    fun createEncodedImage(
        bitmap: Bitmap,
        mimeType: String = "image/jpeg"
    ): EncodedImage? {
        val base64String = encodeToBase64(bitmap)
        return if (base64String != null) {
            EncodedImage(base64String, mimeType)
        } else {
            null
        }
    }
}

/**
 * Represents an encoded image ready for AI processing
 */
data class EncodedImage(
    val base64Data: String,
    val mimeType: String
)