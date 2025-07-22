package com.holder.face.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.holder.face.exception.FaceRecognitionException
import java.io.ByteArrayOutputStream

/**
 * 图片Base64编码工具类
 * 提供Bitmap与Base64字符串之间的转换功能
 */
object ImageBase64Utils {
    
    private const val TAG = "ImageBase64Utils"
    
    // 默认压缩质量
    private const val DEFAULT_QUALITY = 80
    
    // 默认最大尺寸 (像素)
    private const val DEFAULT_MAX_SIZE = 512
    
    /**
     * 将Bitmap转换为Base64字符串
     * @param bitmap 输入的Bitmap
     * @param format 压缩格式 (JPEG或PNG)
     * @param quality 压缩质量 (0-100)
     * @param maxSize 最大尺寸，超过会自动缩放
     * @return Base64编码的字符串
     */
    fun bitmapToBase64(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_QUALITY,
        maxSize: Int = DEFAULT_MAX_SIZE
    ): String {
        try {
            // 1. 检查输入参数
            require(quality in 0..100) { "压缩质量必须在0-100之间" }
            require(maxSize > 0) { "最大尺寸必须大于0" }
            
            // 2. 调整图片尺寸
            val resizedBitmap = resizeBitmapIfNeeded(bitmap, maxSize)
            
            // 3. 压缩为字节数组
            val byteArrayOutputStream = ByteArrayOutputStream()
            val compressSuccess = resizedBitmap.compress(format, quality, byteArrayOutputStream)
            
            if (!compressSuccess) {
                throw FaceRecognitionException.ImageProcessingException("图片压缩失败")
            }
            
            val byteArray = byteArrayOutputStream.toByteArray()
            byteArrayOutputStream.close()
            
            // 4. 释放调整后的Bitmap (如果不是原始Bitmap)
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            
            // 5. 转换为Base64
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            
            Log.d(TAG, "Bitmap转Base64完成: 原始尺寸=${bitmap.width}x${bitmap.height}, " +
                    "压缩后尺寸=${byteArray.size}字节, Base64长度=${base64String.length}")
            
            return base64String
            
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap转Base64失败", e)
            throw FaceRecognitionException.ImageProcessingException("Bitmap转Base64失败", e)
        }
    }
    
    /**
     * 将Base64字符串转换为Bitmap
     * @param base64String Base64编码的字符串
     * @return 解码后的Bitmap
     */
    fun base64ToBitmap(base64String: String): Bitmap {
        try {
            require(base64String.isNotBlank()) { "Base64字符串不能为空" }
            
            // 1. 解码Base64
            val byteArray = Base64.decode(base64String, Base64.NO_WRAP)
            
            // 2. 转换为Bitmap
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                ?: throw FaceRecognitionException.ImageProcessingException("Base64解码为Bitmap失败")
            
            Log.d(TAG, "Base64转Bitmap完成: Base64长度=${base64String.length}, " +
                    "Bitmap尺寸=${bitmap.width}x${bitmap.height}")
            
            return bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Base64转Bitmap失败", e)
            throw FaceRecognitionException.ImageProcessingException("Base64转Bitmap失败", e)
        }
    }
    
    /**
     * 如果需要，调整Bitmap尺寸
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 如果尺寸已经符合要求，直接返回
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        // 计算缩放比例
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Log.d(TAG, "调整图片尺寸: ${width}x${height} -> ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 验证Base64字符串是否为有效的图片
     * @param base64String Base64编码的字符串
     * @return 是否为有效图片
     */
    fun isValidImageBase64(base64String: String?): Boolean {
        if (base64String.isNullOrBlank()) {
            return false
        }
        
        return try {
            val byteArray = Base64.decode(base64String, Base64.NO_WRAP)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            
            // 检查是否成功解析出图片信息
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            Log.w(TAG, "Base64图片验证失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取Base64图片的尺寸信息
     * @param base64String Base64编码的字符串
     * @return Pair<宽度, 高度>，失败返回null
     */
    fun getImageDimensions(base64String: String): Pair<Int, Int>? {
        return try {
            val byteArray = Base64.decode(base64String, Base64.NO_WRAP)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取图片尺寸失败: ${e.message}")
            null
        }
    }
    
    /**
     * 估算Base64字符串对应的图片文件大小 (字节)
     * @param base64String Base64编码的字符串
     * @return 估算的文件大小
     */
    fun estimateImageSize(base64String: String): Int {
        // Base64编码会增加约33%的大小，所以原始大小约为 length * 3 / 4
        return base64String.length * 3 / 4
    }
    
    /**
     * 压缩Base64图片
     * @param base64String 原始Base64字符串
     * @param quality 压缩质量 (0-100)
     * @param maxSize 最大尺寸
     * @return 压缩后的Base64字符串
     */
    fun compressBase64Image(
        base64String: String,
        quality: Int = DEFAULT_QUALITY,
        maxSize: Int = DEFAULT_MAX_SIZE
    ): String {
        try {
            // 1. 解码为Bitmap
            val bitmap = base64ToBitmap(base64String)
            
            // 2. 重新编码
            val compressedBase64 = bitmapToBase64(bitmap, Bitmap.CompressFormat.JPEG, quality, maxSize)
            
            // 3. 释放Bitmap
            bitmap.recycle()
            
            Log.d(TAG, "Base64图片压缩完成: ${base64String.length} -> ${compressedBase64.length}")
            
            return compressedBase64
            
        } catch (e: Exception) {
            Log.e(TAG, "Base64图片压缩失败", e)
            throw FaceRecognitionException.ImageProcessingException("Base64图片压缩失败", e)
        }
    }
    
    /**
     * 创建图片缩略图的Base64
     * @param base64String 原始Base64字符串
     * @param thumbnailSize 缩略图尺寸
     * @return 缩略图的Base64字符串
     */
    fun createThumbnailBase64(
        base64String: String,
        thumbnailSize: Int = 128
    ): String {
        return compressBase64Image(base64String, 70, thumbnailSize)
    }
    
    /**
     * 获取图片格式信息
     * @param base64String Base64编码的字符串
     * @return 图片格式 (如 "JPEG", "PNG" 等)，失败返回null
     */
    fun getImageFormat(base64String: String): String? {
        return try {
            val byteArray = Base64.decode(base64String, Base64.NO_WRAP)
            
            when {
                // JPEG格式标识
                byteArray.size >= 2 && 
                byteArray[0] == 0xFF.toByte() && 
                byteArray[1] == 0xD8.toByte() -> "JPEG"
                
                // PNG格式标识
                byteArray.size >= 8 && 
                byteArray[0] == 0x89.toByte() && 
                byteArray[1] == 0x50.toByte() && 
                byteArray[2] == 0x4E.toByte() && 
                byteArray[3] == 0x47.toByte() -> "PNG"
                
                // WebP格式标识
                byteArray.size >= 12 && 
                byteArray[8] == 0x57.toByte() && 
                byteArray[9] == 0x45.toByte() && 
                byteArray[10] == 0x42.toByte() && 
                byteArray[11] == 0x50.toByte() -> "WebP"
                
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取图片格式失败: ${e.message}")
            null
        }
    }
    
    /**
     * 批量处理图片Base64
     * @param base64List Base64字符串列表
     * @param processor 处理函数
     * @return 处理结果列表
     */
    fun batchProcess(
        base64List: List<String>,
        processor: (String) -> String
    ): List<String> {
        return base64List.mapNotNull { base64 ->
            try {
                processor(base64)
            } catch (e: Exception) {
                Log.w(TAG, "批量处理图片失败: ${e.message}")
                null
            }
        }
    }
}
