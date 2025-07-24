package com.holder.face.utils

import android.graphics.*
import android.media.Image
import android.util.Log
import com.holder.face.exception.FaceRecognitionException
import java.io.ByteArrayOutputStream
import kotlin.math.*
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * 图像处理工具类
 * 提供图像预处理、格式转换等功能
 */
object ImageUtils {

    /**
     * 将Bitmap转换为RGB浮点数组
     * @param bitmap 输入图像
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param normalize 是否归一化到[0,1]范围
     * @return RGB浮点数组 [height, width, 3]
     */
    fun bitmapToFloatArray(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        normalize: Boolean = true
    ): FloatArray {
        try {
            // 缩放图像
            val scaledBitmap = bitmap.scale(targetWidth, targetHeight)

            val pixels = IntArray(targetWidth * targetHeight)
            scaledBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

            val floatArray = FloatArray(targetWidth * targetHeight * 3)
            var index = 0

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                floatArray[index++] = if (normalize) r / 255f else r.toFloat()
                floatArray[index++] = if (normalize) g / 255f else g.toFloat()
                floatArray[index++] = if (normalize) b / 255f else b.toFloat()
            }

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            return floatArray
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "Bitmap转换为浮点数组失败", e
            )
        }
    }

    /**
     * 将Camera2 Image转换为Bitmap
     */
    fun imageToBitmap(image: Image): Bitmap {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "Image转换为Bitmap失败", e
            )
        }
    }

    /**
     * 从Bitmap中裁剪人脸区域
     * @param bitmap 原始图像
     * @param faceRect 人脸矩形区域
     * @param padding 边距比例 (0.0-1.0)
     * @return 裁剪后的人脸图像
     */
    fun cropFace(bitmap: Bitmap, faceRect: Rect, padding: Float = 0.05f): Bitmap {
        try {
            val paddingX = (faceRect.width() * padding).toInt()
            val paddingY = (faceRect.height() * padding).toInt()

            val left = max(0, faceRect.left - paddingX)
            val top = max(0, faceRect.top - paddingY)
            val right = min(bitmap.width, faceRect.right + paddingX)
            val bottom = min(bitmap.height, faceRect.bottom + paddingY)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                throw IllegalArgumentException("无效的裁剪区域")
            }

            return Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "人脸裁剪失败", e
            )
        }
    }

    /**
     * 调整图像大小
     */
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        try {
            return bitmap.scale(targetWidth, targetHeight)
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "图像缩放失败", e
            )
        }
    }

    /**
     * 旋转图像
     * @param bitmap 原始图像
     * @param degrees 旋转角度
     * @return 旋转后的图像
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        try {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "图像旋转失败", e
            )
        }
    }

    /**
     * 图像预处理 (标准化、归一化等)
     * @param bitmap 输入图像
     * @param targetSize 目标尺寸
     * @return 预处理后的浮点数组
     */
    fun preprocessImage(bitmap: Bitmap, targetSize: Int = 112): FloatArray {
        try {
            // 1. 调整大小
            val resized = resizeBitmap(bitmap, targetSize, targetSize)

            // 2. 转换为浮点数组并归一化
            val floatArray = bitmapToFloatArray(resized, targetSize, targetSize, true)

            // 3. 标准化 (可选，根据模型需求)
            // 这里使用ImageNet的均值和标准差
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)

            for (i in floatArray.indices) {
                val channel = i % 3
                floatArray[i] = (floatArray[i] - mean[channel]) / std[channel]
            }

            if (resized != bitmap) {
                resized.recycle()
            }

            return floatArray
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "图像预处理失败", e
            )
        }
    }

    /**
     * 计算图像质量分数
     * @param bitmap 输入图像
     * @return 质量分数 [0, 1]，1表示质量最好
     */
    fun calculateImageQuality(bitmap: Bitmap): Float {
        try {
            // 简单的质量评估：基于图像的方差
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val grayValues = pixels.map { pixel ->
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                (0.299 * r + 0.587 * g + 0.114 * b).toFloat()
            }

            val mean = grayValues.average().toFloat()
            val variance = grayValues.map { (it - mean) * (it - mean) }.average().toFloat()

            // 将方差映射到[0,1]范围，方差越大质量越好
            return min(1f, variance / 10000f)
        } catch (e: Exception) {
            return 0.5f // 默认中等质量
        }
    }


    /**
     * 转换为灰度图像
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        try {
            val grayBitmap = createBitmap(bitmap.width, bitmap.height)
            val canvas = Canvas(grayBitmap)
            val paint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            return grayBitmap
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "灰度转换失败", e
            )
        }
    }

    /**
     * 将Bitmap转换为字节数组
     */
    fun bitmapToByteArray(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): ByteArray {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(format, quality, stream)
            return stream.toByteArray()
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "Bitmap转换为字节数组失败", e
            )
        }
    }

    /**
     * 从字节数组创建Bitmap
     */
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
        try {
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            throw FaceRecognitionException.ImageProcessingException(
                "字节数组转换为Bitmap失败", e
            )
        }
    }
}
