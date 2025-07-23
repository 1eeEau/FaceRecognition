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
            // 1. 图像预处理
            val preprocessedImage = ImageUtils.preprocessImage(faceBitmap, inputSize)

            // 2. 准备输入数据
            val inputBuffer = getOrCreateInputBuffer()

            for (value in preprocessedImage) {
                inputBuffer.putFloat(value)
            }

            // 3. 准备输出数据
            val outputBuffer = getOrCreateOutputBuffer()

            // 4. 执行推理
            interpreter?.run(inputBuffer, outputBuffer)
                ?: throw FaceRecognitionException.FeatureExtractionException("解释器未初始化")

            // 5. 解析输出
            outputBuffer.rewind()
            val features = FloatArray(outputSize)
            for (i in features.indices) {
                features[i] = outputBuffer.float
            }

            // 6. 特征向量归一化
            val normalizedFeatures = normalizeFeatures(features)

            val processingTime = System.currentTimeMillis() - startTime

            if (config.enableDebugLog) {
                Log.d("FeatureExtractor", "特征提取完成: ${processingTime}ms")
                Log.d(
                    "FeatureExtractor",
                    "特征向量范围: [${normalizedFeatures.minOrNull()}, ${normalizedFeatures.maxOrNull()}]"
                )
            }

            return FaceVector(
                personId = personId,
                vector = normalizedFeatures,
                confidence = calculateFeatureQuality(normalizedFeatures)
            )
        } catch (e: FaceRecognitionException) {
            throw e
        } catch (e: Exception) {
            throw FaceRecognitionException.FeatureExtractionException(
                "特征提取失败", e
            )
        }
    }

    private fun getOrCreateInputBuffer(): ByteBuffer {
        if (cachedInputBuffer == null) {
            cachedInputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
                .order(ByteOrder.nativeOrder())
        } else {
            cachedInputBuffer!!.clear()
        }
        return cachedInputBuffer!!;
    }

    private fun getOrCreateOutputBuffer(): ByteBuffer {
        if (cachedOutputBuffer == null) {
            cachedOutputBuffer = ByteBuffer.allocateDirect(4 * outputSize)
                .order(ByteOrder.nativeOrder())
        } else {
            cachedOutputBuffer!!.clear()
        }
        return cachedOutputBuffer!!
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
            // 基于特征向量的统计特性计算质量分数
            val mean = features.average().toFloat()
            val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance)

            // 特征分布越均匀，质量越好
            val uniformityScore = 1f - kotlin.math.abs(mean)
            val diversityScore = kotlin.math.min(1f, stdDev * 2f)

            // 检查是否有异常值
            val outlierCount = features.count { kotlin.math.abs(it) > 3f }
            val outlierPenalty = outlierCount.toFloat() / features.size

            val qualityScore = (uniformityScore + diversityScore) / 2f - outlierPenalty
            return kotlin.math.max(0f, kotlin.math.min(1f, qualityScore))
        } catch (e: Exception) {
            return 0.5f // 默认中等质量
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
            val dummyInput = getOrCreateInputBuffer()

            val dummyOutput = getOrCreateOutputBuffer()

            interpreter?.run(dummyInput, dummyOutput)

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
