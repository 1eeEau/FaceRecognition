package com.holder.face.core

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.holder.face.config.FaceRecognitionConfig
import com.holder.face.exception.FaceRecognitionException
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import androidx.core.graphics.scale
import com.holder.face.model.RecognitionResult

/**
 * 人脸检测器
 * 基于Google MLKit的人脸检测功能
 */
class FaceDetector(private val config: FaceRecognitionConfig) {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f) // 最小人脸尺寸比例
            .enableTracking() // 启用人脸跟踪
            .build()

        FaceDetection.getClient(options)
    }

    /**
     * 检测结果数据类
     */
    data class DetectionResult(
        val faces: List<DetectedFace>,
        val processingTime: Long,
        val imageWidth: Int,
        val imageHeight: Int
    ) {
        val faceCount: Int get() = faces.size
        val hasFaces: Boolean get() = faces.isNotEmpty()
        val hasMultipleFaces: Boolean get() = faces.size > 1
    }

    /**
     * 检测到的人脸信息
     */
    data class DetectedFace(
        val boundingBox: Rect,
        val confidence: Float,
        val trackingId: Int?,
        val rotationY: Float,
        val rotationZ: Float,
        val smilingProbability: Float?,
        val leftEyeOpenProbability: Float?,
        val rightEyeOpenProbability: Float?
    ) {
        /**
         * 检查人脸质量
         */
        fun isGoodQuality(): Boolean {
            return confidence >= 0.7f &&
                    kotlin.math.abs(rotationY) < 30f &&
                    kotlin.math.abs(rotationZ) < 30f &&
                    (leftEyeOpenProbability ?: 1f) > 0.3f &&
                    (rightEyeOpenProbability ?: 1f) > 0.3f
        }

        /**
         * 获取人脸尺寸
         */
        fun getFaceSize(): Int {
            return kotlin.math.max(boundingBox.width(), boundingBox.height())
        }

        /**
         * 检查人脸尺寸是否符合要求
         */
        fun isSizeValid(minSize: Int, maxSize: Int): Boolean {
            val size = getFaceSize()
            return size in minSize..maxSize
        }
    }

    /**
     * 检测图像中的人脸
     * @param bitmap 输入图像
     * @return 检测结果
     */
    suspend fun detectFaces(bitmap: Bitmap): DetectionResult {
        val startTime = System.currentTimeMillis()

        try {
            val maxDetectionSize = config.maxDetectionImageSize

            val scaledBitmap =
                if (bitmap.width > maxDetectionSize || bitmap.height > maxDetectionSize) {
                    val scale = maxDetectionSize.toFloat() / max(bitmap.width, bitmap.height)
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()

                    if (config.enableDebugLog) {
                        Log.d(
                            "FaceDetector",
                            "图像缩放 ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}"
                        )
                    }
                    bitmap.scale(newWidth, newHeight)
                } else {
                    bitmap
                }

            val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
            val faces = detector.process(inputImage).await()

            // 计算缩放比例，用于还原坐标
            val scaleX = bitmap.width.toFloat() / scaledBitmap.width
            val scaleY = bitmap.height.toFloat() / scaledBitmap.height

            val detectedFaces = faces.map { face ->
                convertToDetectedFace(face, scaleX, scaleY)
            }.filter { detectedFace ->
                // 增强边界检查
                isValidBoundingBox(detectedFace.boundingBox, bitmap.width, bitmap.height) &&
                        detectedFace.confidence >= config.faceDetectionConfidence &&
                        detectedFace.isSizeValid(config.minFaceSize, config.maxFaceSize)
            }

            val processingTime = System.currentTimeMillis() - startTime

            // 确保释放缩放图
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            if (config.enableDebugLog) {
                Log.d(
                    "FaceDetector",
                    "人脸检测耗时: ${processingTime}ms, 检测到${detectedFaces.size}个人脸"
                )
            }

            return DetectionResult(
                faces = detectedFaces,
                processingTime = processingTime,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            throw FaceRecognitionException.FaceDetectionException("人脸检测失败", e)
        }
    }

    suspend fun detectLargestFace(bitmap: Bitmap): DetectedFace {
        val detectionResult = detectFaces(bitmap)
        return when {
            detectionResult.faces.isEmpty() -> {
                throw FaceRecognitionException.FaceDetectionException("未检测到人脸")
            }
            // 2. 选择最佳人脸（如果有多个人脸，选择占比最大且质量最好的）
            detectionResult.faces.size > 1 -> {
                // 综合考虑人脸大小选择最佳人脸
                val bestFace = detectionResult.faces.maxByOrNull { face ->
                    (face.boundingBox.width() * face.boundingBox.height()).toFloat()
                }

                if (config.enableDebugLog) {
                    Log.i(
                        "FaceRecognitionManager",
                        "检测到${detectionResult.faces.size}个人脸，选择最佳人脸进行识别"
                    )
                    Log.i(
                        "FaceRecognitionManager",
                        "选中人脸大小: ${bestFace?.getFaceSize()}, 质量: ${bestFace?.isGoodQuality()}"
                    )
                }
                bestFace!!
            }

            else -> detectionResult.faces.first()
        }
    }

    /**
     * 检测单个人脸 (确保只有一个人脸)
     * @param bitmap 输入图像
     * @return 检测到的人脸，如果没有或有多个人脸则抛出异常
     */
    suspend fun detectSingleFace(bitmap: Bitmap): DetectedFace {
        val result = detectFaces(bitmap)

        return when {
            result.faces.isEmpty() -> {
                throw FaceRecognitionException.FaceDetectionException("未检测到人脸")
            }

            result.faces.size > 1 -> {
                throw FaceRecognitionException.FaceDetectionException(
                    "检测到多个人脸 (${result.faces.size} 个)，请确保图像中只有一个人脸"
                )
            }

            else -> {
                val face = result.faces.first()
                if (!face.isGoodQuality()) {
                    if (config.enableDebugLog) {
                        Log.i(
                            "FaceDetector",
                            "人脸质量较低: confidence=${face.confidence}, rotationY=${face.rotationY}, rotationZ=${face.rotationZ}"
                        )
                    }
                }
                face
            }
        }
    }

    /**
     * 获取最佳质量的人脸
     * @param bitmap 输入图像
     * @return 质量最好的人脸，如果没有人脸则返回null
     */
    suspend fun getBestQualityFace(bitmap: Bitmap): DetectedFace? {
        val result = detectFaces(bitmap)

        return result.faces
            .filter { it.isGoodQuality() }
            .maxByOrNull { calculateFaceScore(it) }
    }

    /**
     * 计算人脸质量分数
     */
    private fun calculateFaceScore(face: DetectedFace): Float {
        var score = face.confidence

        // 尺寸分数 (中等尺寸最好)
        val size = face.getFaceSize()
        val optimalSize = (config.minFaceSize + config.maxFaceSize) / 2
        val sizeScore = 1f - kotlin.math.abs(size - optimalSize) / optimalSize.toFloat()
        score += sizeScore * 0.3f

        // 角度分数 (正面最好)
        val angleScore =
            1f - (kotlin.math.abs(face.rotationY) + kotlin.math.abs(face.rotationZ)) / 60f
        score += angleScore * 0.2f

        // 眼睛睁开分数
        val eyeScore =
            ((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f
        score += eyeScore * 0.1f

        return score
    }

    /**
     * 转换MLKit Face对象为DetectedFace
     */
    private fun convertToDetectedFace(face: Face, scaleX: Float, scaleY: Float): DetectedFace {
        // 确保坐标转换的精确性
        val left = (face.boundingBox.left * scaleX).toInt()
        val top = (face.boundingBox.top * scaleY).toInt()
        val right = (face.boundingBox.right * scaleX).toInt()
        val bottom = (face.boundingBox.bottom * scaleY).toInt()

        val originalBoundingBox = Rect(left, top, right, bottom)

        if (config.enableDebugLog) {
            Log.d(
                "FaceDetector",
                "坐标转换: 缩放图${face.boundingBox} -> 原图${originalBoundingBox}"
            )
        }

        return DetectedFace(
            boundingBox = originalBoundingBox,
            confidence = 1.0f,
            trackingId = face.trackingId,
            rotationY = face.headEulerAngleY,
            rotationZ = face.headEulerAngleZ,
            smilingProbability = face.smilingProbability,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability
        )
    }

    /**
     * 验证检测结果
     */
    fun validateDetectionResult(result: DetectionResult): Boolean {
        return result.faces.all { face ->
            face.boundingBox.left >= 0 &&
                    face.boundingBox.top >= 0 &&
                    face.boundingBox.right <= result.imageWidth &&
                    face.boundingBox.bottom <= result.imageHeight &&
                    face.confidence > 0f
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            detector.close()
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceDetector", "释放人脸检测器资源失败: ${e.message}")
            }
        }
    }

    /**
     * 检查边界框是否有效
     */
    private fun isValidBoundingBox(rect: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        return rect.left >= 0 &&
                rect.top >= 0 &&
                rect.right <= imageWidth &&
                rect.bottom <= imageHeight &&
                rect.width() > 0 &&
                rect.height() > 0
    }
}
