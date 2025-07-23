package com.holder.face.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.holder.face.config.FaceRecognitionConfig
import com.holder.face.exception.FaceRecognitionException
import com.holder.face.model.FaceVector
import com.holder.face.utils.ImageUtils
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * 人脸特征提取器
 * 基于TensorFlow Lite模型提取人脸特征向量
 */
class FeatureExtractor(
    private val context: Context,
    private val config: FaceRecognitionConfig
) {

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    // 模型输入输出配置
    private val inputSize = config.featureInputSize // MobileFaceNet输入尺寸
    private val outputSize = config.featureVectorDimension

    /**
     * 缓存byteBuffer，避免重复分配
     */
    private var cachedInputBuffer: ByteBuffer? = null
    private var cachedOutputBuffer: ByteBuffer? = null

    /**interpreter
     * 初始化特征提取器
     */
    suspend fun initialize() {
        try {
            if (isInitialized) return

            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                numThreads = 4 // 使用4个线程
                useNNAPI = true // 启用NNAPI加速
            }

            interpreter = Interpreter(modelBuffer, options)

            warmUpModel()

            isInitialized = true

            if (config.enableDebugLog) {
                Log.i("FeatureExtractor", "特征提取器初始化成功")
                Log.i("FeatureExtractor", "输入尺寸：${inputSize}x${inputSize}")
                Log.i("FeatureExtractor", "输出纬度：${outputSize}")
            }
        } catch (e: Exception) {
            throw FaceRecognitionException.InitializationException(
                "特征提取器初始化失败", e
            )
        }
    }

    /**
     * 从人脸图像提取特征向量
     * @param faceBitmap 人脸图像 (应该是裁剪后的人脸区域)
     * @param personId 人员ID
     * @return 人脸特征向量
     */
    suspend fun extractFeatures(faceBitmap: Bitmap, personId: String): FaceVector {
        if (!isInitialized) {
            throw FaceRecognitionException.InitializationException("特征提取器未初始化")
        }

        val startTime = System.currentTimeMillis()

        try {
            // 1. 图像预处理 - 直接输出到ByteBuffer
            val inputBuffer = getOrCreateInputBuffer()
            preprocessImageToBuffer(faceBitmap, inputBuffer)

            // 2. 准备输出数据
            val outputBuffer = getOrCreateOutputBuffer()

            // 3. 执行推理
            interpreter?.run(inputBuffer, outputBuffer)
                ?: throw FaceRecognitionException.FeatureExtractionException("解释器未初始化")

            // 4. 解析输出并归一化
            val normalizedFeatures = extractAndNormalizeFeatures(outputBuffer)

            val processingTime = System.currentTimeMillis() - startTime

            if (config.enableDebugLog) {
                Log.d("FeatureExtractor", "特征提取完成: ${processingTime}ms")
            }

            return FaceVector(
                personId = personId,
                vector = normalizedFeatures,
                confidence = calculateFeatureQuality(normalizedFeatures)
            )
        } catch (e: FaceRecognitionException) {
            throw e
        } catch (e: Exception) {
            throw FaceRecognitionException.FeatureExtractionException("特征提取失败", e)
        }
    }

    private fun getOrCreateInputBuffer(): ByteBuffer {
        if (cachedInputBuffer == null) {
            val bufferSize = 4 * inputSize * inputSize * 3
            cachedInputBuffer = ByteBuffer.allocateDirect(bufferSize)
                .order(ByteOrder.nativeOrder())
        } else {
            cachedInputBuffer!!.rewind()
        }
        return cachedInputBuffer!!
    }

    private fun getOrCreateOutputBuffer(): ByteBuffer {
        if (cachedOutputBuffer == null) {
            val bufferSize = 4 * outputSize
            cachedOutputBuffer = ByteBuffer.allocateDirect(bufferSize)
                .order(ByteOrder.nativeOrder())
        } else {
            cachedOutputBuffer!!.rewind()
        }
        return cachedOutputBuffer!!
    }

    /**
     * 直接将图像预处理到ByteBuffer，避免中间数组
     */
    private fun preprocessImageToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()

        // 缩放图像
        val resized = if (bitmap.width != inputSize || bitmap.height != inputSize) {
            bitmap.scale(inputSize, inputSize)
        } else {
            bitmap
        }

        // 直接提取像素并写入buffer
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // ImageNet标准化参数
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // 标准化并直接写入buffer
            buffer.putFloat((r - mean[0]) / std[0])
            buffer.putFloat((g - mean[1]) / std[1])
            buffer.putFloat((b - mean[2]) / std[2])
        }

        // 释放临时bitmap
        if (resized != bitmap) {
            resized.recycle()
        }
    }

    /**
     * 直接从ByteBuffer提取并归一化特征，减少数组拷贝
     */
    private fun extractAndNormalizeFeatures(outputBuffer: ByteBuffer): FloatArray {
        outputBuffer.rewind()

        val features = FloatArray(outputSize)
        var norm = 0f

        // 第一遍：读取数据并计算L2范数
        for (i in features.indices) {
            val value = outputBuffer.float
            features[i] = value
            norm += value * value
        }

        // 归一化
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in features.indices) {
                features[i] /= norm
            }
        }

        return features
    }

    /**
     * 批量提取特征
     * @param faceBitmaps 人脸图像列表
     * @param personIds 对应的人员ID列表
     * @return 特征向量列表
     */
    suspend fun extractFeaturesInBatch(
        faceBitmaps: List<Bitmap>,
        personIds: List<String>
    ): List<FaceVector> {
        require(faceBitmaps.size == personIds.size) {
            "图像数量与人员ID数量不匹配"
        }

        val results = mutableListOf<FaceVector>()

        for (i in faceBitmaps.indices) {
            try {
                val faceVector = extractFeatures(faceBitmaps[i], personIds[i])
                results.add(faceVector)
            } catch (e: Exception) {
                if (config.enableDebugLog) {
                    Log.d("FeatureExtractor", "批量提取第${i}个特征失败: ${e.message}")
                }
                // 继续处理其他图像
            }
        }

        return results
    }

    /**
     * 加载模型文件
     */
    private fun loadModelFile(): MappedByteBuffer {
        try {
            val assetFileDescriptor = context.assets.openFd(config.modelFileName)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            throw FaceRecognitionException.ModelLoadException(
                config.modelFileName, e
            )
        }
    }

    /**
     * 特征向量归一化
     */
    private fun normalizeFeatures(features: FloatArray): FloatArray {
        try {
            // L2归一化
            var norm = 0f
            for (value in features) {
                norm += value * value
            }
            norm = kotlin.math.sqrt(norm)

            if (norm == 0f) {
                return features.clone()
            }

            return FloatArray(features.size) { i ->
                features[i] / norm
            }
        } catch (e: Exception) {
            throw FaceRecognitionException.FeatureExtractionException(
                "特征归一化失败", e
            )
        }
    }

    /**
     * 计算特征质量分数
     */
    private fun calculateFeatureQuality(features: FloatArray): Float {
        try {
            // 简化计算：基于特征向量的方差
            var sum = 0f
            var sumSquares = 0f

            for (value in features) {
                sum += value
                sumSquares += value * value
            }

            val mean = sum / features.size
            val variance = (sumSquares / features.size) - (mean * mean)

            // 将方差映射到[0.5, 1.0]范围
            return 0.5f + kotlin.math.min(0.5f, variance * 2f)
        } catch (e: Exception) {
            return 0.8f // 默认较高质量
        }
    }

    /**
     * 验证特征向量
     */
    fun validateFeatures(features: FloatArray): Boolean {
        return features.size == outputSize &&
                features.all { it.isFinite() && !it.isNaN() } &&
                features.any { it != 0f } // 不全为零
    }

    /**
     * 获取模型信息
     */
    fun getModelInfo(): Map<String, Any> {
        return mapOf(
            "modelFileName" to config.modelFileName,
            "inputSize" to inputSize,
            "outputSize" to outputSize,
            "isInitialized" to isInitialized,
            "interpreterVersion" to (interpreter?.let { "TensorFlow Lite" } ?: "未初始化")
        )
    }

    /**
     * 预热模型
     */
    private fun warmUpModel() {
        try {
            // 预热多次以确保JIT优化
            repeat(3) {
                val dummyInput = getOrCreateInputBuffer()
                val dummyOutput = getOrCreateOutputBuffer()
                interpreter?.run(dummyInput, dummyOutput)
            }

            if (config.enableDebugLog) {
                Log.d("FeatureExtractor", "模型预热完成")
            }
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.w("FeatureExtractor", "模型预热失败： ${e.message}")
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false

            if (config.enableDebugLog) {
                Log.i("FeatureExtractor", "特征提取器资源已释放")
            }
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FeatureExtractor", "释放特征提取器资源失败: ${e.message}")
            }
        }
    }
}
