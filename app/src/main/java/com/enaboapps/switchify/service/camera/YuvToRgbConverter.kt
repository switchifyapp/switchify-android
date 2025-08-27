package com.enaboapps.switchify.service.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for efficiently converting YUV_420_888 camera frames to RGB bitmaps.
 * Uses pure Kotlin implementation to avoid deprecated RenderScript issues.
 * Reuses buffers to minimize per-frame allocations.
 */
class YuvToRgbConverter {
    
    // Reusable buffers to avoid per-frame allocations
    private var rgbBuffer: IntArray? = null
    
    // Track current dimensions to know when to recreate buffers
    private var currentWidth = 0
    private var currentHeight = 0
    
    companion object {
        private const val TAG = "YuvToRgbConverter"
        
        // Buffer size threshold - recreate if size changes significantly
        private const val BUFFER_SIZE_THRESHOLD = 1.2f
    }
    
    /**
     * Convert YUV_420_888 Image to RGB Bitmap efficiently.
     * Reuses internal buffers when possible.
     */
    fun convertYuvToBitmap(image: Image): Bitmap? {
        return try {
            val width = image.width
            val height = image.height
            
            if (image.format != ImageFormat.YUV_420_888) {
                Log.e(TAG, "Unsupported image format: ${image.format}")
                return null
            }
            
            val planes = image.planes
            if (planes.size != 3) {
                Log.e(TAG, "Expected 3 planes, got ${planes.size}")
                return null
            }
            
            // Convert YUV to RGB
            val rgbArray = convertYuvToRgb(image, width, height)
            if (rgbArray == null) {
                Log.e(TAG, "Failed to convert YUV to RGB")
                return null
            }
            
            // Create bitmap from RGB data
            Bitmap.createBitmap(rgbArray, width, height, Bitmap.Config.ARGB_8888)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV to bitmap", e)
            null
        }
    }
    
    /**
     * Convert YUV_420_888 Image to RGB Int array.
     */
    private fun convertYuvToRgb(image: Image, width: Int, height: Int): IntArray? {
        return try {
            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val yBytes = ByteArray(ySize)
            val uBytes = ByteArray(uSize)
            val vBytes = ByteArray(vSize)
            
            yBuffer.get(yBytes)
            uBuffer.get(uBytes)
            vBuffer.get(vBytes)
            
            val rgbArray = ensureRgbBuffer(width * height)
            
            // Convert YUV to RGB
            var rgbIndex = 0
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val yIndex = row * yPlane.rowStride + col * yPlane.pixelStride
                    val uvRow = row / 2
                    val uvCol = col / 2
                    val uIndex = uvRow * uPlane.rowStride + uvCol * uPlane.pixelStride
                    val vIndex = uvRow * vPlane.rowStride + uvCol * vPlane.pixelStride
                    
                    if (yIndex < yBytes.size && uIndex < uBytes.size && vIndex < vBytes.size) {
                        val y = (yBytes[yIndex].toInt() and 0xFF) - 16
                        val u = (uBytes[uIndex].toInt() and 0xFF) - 128
                        val v = (vBytes[vIndex].toInt() and 0xFF) - 128
                        
                        // YUV to RGB conversion
                        val r = (1.164f * y + 1.596f * v).toInt()
                        val g = (1.164f * y - 0.813f * v - 0.391f * u).toInt()
                        val b = (1.164f * y + 2.018f * u).toInt()
                        
                        // Clamp values and create ARGB int
                        rgbArray[rgbIndex++] = (0xFF shl 24) or 
                                              ((clamp(r) and 0xFF) shl 16) or 
                                              ((clamp(g) and 0xFF) shl 8) or 
                                              (clamp(b) and 0xFF)
                    } else {
                        rgbArray[rgbIndex++] = 0xFF000000.toInt() // Black pixel
                    }
                }
            }
            
            currentWidth = width
            currentHeight = height
            
            rgbArray
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in YUV to RGB conversion", e)
            null
        }
    }
    
    /**
     * Clamp value to 0-255 range
     */
    private fun clamp(value: Int): Int {
        return max(0, min(255, value))
    }
    
    /**
     * Ensure RGB buffer is allocated and sized appropriately.
     */
    private fun ensureRgbBuffer(requiredSize: Int): IntArray {
        if (rgbBuffer == null || rgbBuffer!!.size < requiredSize || 
            rgbBuffer!!.size > (requiredSize * BUFFER_SIZE_THRESHOLD).toInt()) {
            rgbBuffer = IntArray(requiredSize)
            Log.d(TAG, "Created new RGB buffer of size $requiredSize")
        }
        return rgbBuffer!!
    }
    
    /**
     * Clean up resources.
     */
    fun cleanup() {
        try {
            // Clear buffers to free memory
            rgbBuffer = null
            
            currentWidth = 0
            currentHeight = 0
            
            Log.d(TAG, "YuvToRgbConverter cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}