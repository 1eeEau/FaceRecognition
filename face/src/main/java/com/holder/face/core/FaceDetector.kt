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
            return size >= minSize && size <= maxSize
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
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(inputImage).await()

            val detectedFaces = faces.map { face ->
                convertToDetectedFace(face)
            }.filter { detectedFace ->
                // 过滤不符合要求的人脸
                detectedFace.confidence >= config.faceDetectionConfidence &&
                        detectedFace.isSizeValid(config.minFaceSize, config.maxFaceSize)
            }

            val processingTime = System.currentTimeMillis() - startTime

            return DetectionResult(
                faces = detectedFaces,
                processingTime = processingTime,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        } catch (e: Exception) {
            throw FaceRecognitionException.FaceDetectionException(
                "人脸检测失败", e
            )
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
    private fun convertToDetectedFace(face: Face): DetectedFace {
        return DetectedFace(
            boundingBox = face.boundingBox,
            confidence = 1.0f, // MLKit不直接提供置信度，使用默认值
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
}
