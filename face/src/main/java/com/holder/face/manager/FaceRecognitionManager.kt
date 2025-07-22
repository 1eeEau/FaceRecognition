package com.holder.face.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.holder.face.config.FaceRecognitionConfig
import com.holder.face.core.FaceComparator
import com.holder.face.core.FaceDetector
import com.holder.face.core.FeatureExtractor
import com.holder.face.database.FaceDatabase
import com.holder.face.database.FaceRepository
import com.holder.face.exception.FaceRecognitionException
import com.holder.face.model.FaceVector
import com.holder.face.model.RecognitionResult
import com.holder.face.utils.ImageUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/**
 * 人脸识别管理器
 * 提供完整的人脸识别功能API
 */
class FaceRecognitionManager private constructor(
    private val context: Context,
    private val config: FaceRecognitionConfig
) {

    // 核心组件
    private lateinit var faceDetector: FaceDetector
    private lateinit var featureExtractor: FeatureExtractor
    private lateinit var faceComparator: FaceComparator
    private lateinit var faceRepository: FaceRepository

    // 状态管理
    private var isInitialized = false
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        private var INSTANCE: FaceRecognitionManager? = null

        /**
         * 获取单例实例
         */
        fun getInstance(
            context: Context,
            config: FaceRecognitionConfig = FaceRecognitionConfig.default()
        ): FaceRecognitionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FaceRecognitionManager(context.applicationContext, config)
                INSTANCE = instance
                instance
            }
        }

        /**
         * 清除实例 (用于测试或重新配置)
         */
        fun clearInstance() {
            INSTANCE?.release()
            INSTANCE = null
        }
    }

    /**
     * 初始化人脸识别系统
     */
    suspend fun initialize() {
        if (isInitialized) return

        try {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "开始初始化人脸系统")
            }

            // 验证配置
            if (!config.validate()) {
                throw FaceRecognitionException.ConfigurationException("配置验证失败")
            }

            // 初始化数据库
            val database = FaceDatabase.getDatabase(context, config.databaseName)
            faceRepository = FaceRepository(database.faceDao(), config)

            // 初始化核心组件
            faceDetector = FaceDetector(config)
            featureExtractor = FeatureExtractor(context, config)
            faceComparator = FaceComparator(config)

            // 初始化特征提取器
            featureExtractor.initialize()

            isInitialized = true

            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "人脸识别系统初始化完成")
                Log.i("FaceRecognitionManager", "配置信息： $config")
                Log.i("FaceRecognitionManager", "当前人脸数量： ${faceRepository.getFaceCount()}")
            }
        } catch (e: Exception) {
            throw FaceRecognitionException.InitializationException(
                "人脸识别系统初始化失败", e
            )
        }
    }

    /**
     * 注册人脸
     * @param bitmap 人脸图像
     * @param personId 人员ID (后端返回的ID)
     * @param remarks 备注信息
     * @return 注册结果
     */
    suspend fun registerFace(
        bitmap: Bitmap,
        personId: String,
        remarks: String? = null
    ): RecognitionResult {
        ensureInitialized()
        val startTime = System.currentTimeMillis()

        try {
            // 1. 检查存储空间
            if (faceRepository.isStorageFull()) {
                return RecognitionResult.failure(
                    "存储空间已满，最大支持 ${config.maxFaceCount} 个人脸",
                    System.currentTimeMillis() - startTime
                )
            }

            // 2. 人脸检测
            val detectedFace = faceDetector.detectSingleFace(bitmap)

            // 3. 裁剪人脸区域
            val faceBitmap = ImageUtils.cropFace(bitmap, detectedFace.boundingBox)

            // 5. 提取特征
            val faceVector = featureExtractor.extractFeatures(faceBitmap, personId)

            // 6. 存储到数据库
            val recordId = faceRepository.addFace(faceVector, remarks)

            val processingTime = System.currentTimeMillis() - startTime

            if (config.enableDebugLog) {
                Log.i(
                    "FaceRecognitionManager",
                    "人脸注册成功: personId=$personId, recordId=$recordId, time=${processingTime}ms"
                )
            }

            return RecognitionResult.success(
                personId = personId,
                confidence = faceVector.confidence ?: 1.0f,
                processingTime = processingTime,
                extras = mapOf(
                    "recordId" to recordId,
                    "faceSize" to detectedFace.getFaceSize(),
                    "faceQuality" to detectedFace.isGoodQuality()
                )
            )
        } catch (e: FaceRecognitionException) {
            return RecognitionResult.failure(
                e.message ?: "注册失败",
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return RecognitionResult.failure(
                "注册过程中发生未知错误: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * 识别人脸
     * @param bitmap 待识别的人脸图像
     * @return 识别结果
     */
    suspend fun recognizeFace(bitmap: Bitmap): RecognitionResult {
        ensureInitialized()
        val startTime = System.currentTimeMillis()

        try {
            // 1. 人脸检测
            val detectionResult = faceDetector.detectFaces(bitmap)

            when {
                detectionResult.faces.isEmpty() -> {
                    return RecognitionResult.noFaceDetected(
                        System.currentTimeMillis() - startTime
                    )
                }

                detectionResult.faces.size > 1 -> {
                    return RecognitionResult.multipleFacesDetected(
                        detectionResult.faces.size,
                        System.currentTimeMillis() - startTime
                    )
                }
            }

            val detectedFace = detectionResult.faces.first()

            // 2. 裁剪人脸区域
            val faceBitmap = ImageUtils.cropFace(bitmap, detectedFace.boundingBox)

            // 3. 提取特征
            val queryVector = featureExtractor.extractFeatures(faceBitmap, "query")

            // 4. 获取所有已注册的人脸
            val registeredFaces = faceRepository.getAllEnabledFaces()

            if (registeredFaces.isEmpty()) {
                return RecognitionResult.failure(
                    "没有已注册的人脸数据",
                    System.currentTimeMillis() - startTime
                )
            }

            // 5. 人脸比较
            val bestMatch = faceComparator.findBestMatch(queryVector, registeredFaces)

            val processingTime = System.currentTimeMillis() - startTime

            return if (bestMatch?.isMatch == true) {
                RecognitionResult.success(
                    personId = bestMatch.personId,
                    confidence = bestMatch.similarity,
                    processingTime = processingTime,
                    extras = mapOf(
                        "distance" to bestMatch.distance,
                        "method" to bestMatch.method,
                        "faceSize" to detectedFace.getFaceSize(),
                        "registeredCount" to registeredFaces.size
                    )
                )
            } else {
                RecognitionResult.noMatch(
                    processingTime = processingTime,
                    extras = mapOf(
                        "bestSimilarity" to (bestMatch?.similarity ?: 0f),
                        "threshold" to config.recognitionThreshold,
                        "registeredCount" to registeredFaces.size
                    )
                )
            }
        } catch (e: FaceRecognitionException) {
            return RecognitionResult.failure(
                e.message ?: "识别失败",
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return RecognitionResult.failure(
                "识别过程中发生未知错误: ${e.message}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * 删除人脸数据
     */
    suspend fun deleteFace(personId: String): Boolean {
        ensureInitialized()
        return try {
            faceRepository.deleteFace(personId)
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "删除人脸失败：${e.message}")
            }
            false
        }
    }

    /**
     * 获取人脸数据
     */
    suspend fun getFace(personId: String): FaceVector? {
        ensureInitialized()
        return try {
            faceRepository.getFace(personId)
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "获取人脸数据失败: ${e.message}")
            }
            null
        }
    }

    /**
     * 获取所有人脸数据
     */
    suspend fun getAllFaces(): List<FaceVector> {
        ensureInitialized()
        return try {
            faceRepository.getAllEnabledFaces()
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "获取人脸列表失败: ${e.message}")
            }
            emptyList()
        }
    }

    /**
     * 获取人脸数据流 (用于实时更新)
     */
    fun getAllFacesFlow(): Flow<List<FaceVector>> {
        ensureInitialized()
        return faceRepository.getAllEnabledFacesFlow()
    }

    /**
     * 获取人脸数量
     */
    suspend fun getFaceCount(): Int {
        ensureInitialized()
        return try {
            faceRepository.getFaceCount()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取剩余存储容量
     */
    suspend fun getRemainingCapacity(): Int {
        ensureInitialized()
        return try {
            faceRepository.getRemainingCapacity()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 清空所有人脸数据
     */
    suspend fun clearAllFaces(): Boolean {
        ensureInitialized()
        return try {
            faceRepository.clearAllData() > 0
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "清空人脸数据失败: ${e.message}")
            }
            false
        }
    }

    /**
     * 获取系统状态信息
     */
    suspend fun getSystemStatus(): Map<String, Any> {
        return try {
            val stats = if (isInitialized) faceRepository.getDatabaseStats() else null
            mapOf(
                "isInitialized" to isInitialized,
                "config" to config,
                "faceCount" to (stats?.enabled_count ?: 0),
                "totalCapacity" to config.maxFaceCount,
                "remainingCapacity" to getRemainingCapacity(),
                "databaseStats" to (stats ?: "未初始化"),
                "modelInfo" to if (isInitialized) featureExtractor.getModelInfo() else "未初始化"
            )
        } catch (e: Exception) {
            mapOf(
                "error" to e.message!!,
                "isInitialized" to isInitialized,
            )
        }
    }

    /**
     * 确保系统已初始化
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            throw FaceRecognitionException.InitializationException("人脸识别系统未初始化，请先调用initialize()")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            if (::faceDetector.isInitialized) {
                faceDetector.release()
            }
            if (::featureExtractor.isInitialized) {
                featureExtractor.release()
            }

            initializationScope.cancel()
            isInitialized = false

            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "人脸识别系统资源已释放")
            }
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.i("FaceRecognitionManager", "释放资源失败: ${e.message}")
            }
        }
    }
}
