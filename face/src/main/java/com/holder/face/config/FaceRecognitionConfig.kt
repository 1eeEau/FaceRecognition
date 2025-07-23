package com.holder.face.config

/**
 * 人脸识别配置类
 * 提供可配置的参数设置，支持Builder模式
 */
data class FaceRecognitionConfig(
    /** 最大人脸存储数量 */
    val maxFaceCount: Int = DEFAULT_MAX_FACE_COUNT,

    /** 人脸识别相似度阈值 (0.0-1.0) */
    val recognitionThreshold: Float = DEFAULT_RECOGNITION_THRESHOLD,

    /** 特征向量维度 */
    val featureVectorDimension: Int = DEFAULT_FEATURE_VECTOR_DIMENSION,

    /** 模型输入 */
    val featureInputSize: Int = DEFAULT_FEATURE_INPUT_SIZE,

    /** 最小人脸尺寸 (像素) */
    val minFaceSize: Int = DEFAULT_MIN_FACE_SIZE,

    /** 最大人脸尺寸 (像素) */
    val maxFaceSize: Int = DEFAULT_MAX_FACE_SIZE,

    /** 是否启用调试日志 */
    val enableDebugLog: Boolean = false,

    /** 人脸检测置信度阈值 */
    val faceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE,

    /** 数据库名称 */
    val databaseName: String = DEFAULT_DATABASE_NAME,

    /** TensorFlow Lite模型文件名 */
    val modelFileName: String = DEFAULT_MODEL_FILE_NAME,

    /** 向量相似度计算方式 */
    val similarityMethod: SimilarityMethod = SimilarityMethod.COSINE
) {

    companion object {
        // 默认配置常量
        const val DEFAULT_MAX_FACE_COUNT = 50
        const val DEFAULT_RECOGNITION_THRESHOLD = 0.85f
        const val DEFAULT_FEATURE_VECTOR_DIMENSION = 512
        const val DEFAULT_FEATURE_INPUT_SIZE = 112
        const val DEFAULT_MIN_FACE_SIZE = 80
        const val DEFAULT_MAX_FACE_SIZE = 800
        const val DEFAULT_FACE_DETECTION_CONFIDENCE = 0.8f
        const val DEFAULT_DATABASE_NAME = "face_recognition.db"
        const val DEFAULT_MODEL_FILE_NAME = "MobileFaceNet.tflite"

        /**
         * 获取默认配置
         */
        fun default(): FaceRecognitionConfig = FaceRecognitionConfig()

        /**
         * 创建Builder实例
         */
        fun builder(): Builder = Builder()
    }

    /**
     * 相似度计算方式枚举
     */
    enum class SimilarityMethod {
        COSINE,     // 余弦相似度
        EUCLIDEAN,  // 欧几里得距离
        MANHATTAN   // 曼哈顿距离
    }

    /**
     * 配置验证
     * @return 配置是否有效
     */
    fun validate(): Boolean {
        return maxFaceCount > 0 &&
                recognitionThreshold in 0.0f..1.0f &&
                featureVectorDimension > 0 &&
                featureInputSize > 0 &&
                minFaceSize > 0 &&
                maxFaceSize > minFaceSize &&
                faceDetectionConfidence in 0.0f..1.0f &&
                databaseName.isNotBlank() &&
                modelFileName.isNotBlank()
    }

    /**
     * Builder模式构建器
     */
    class Builder {
        private var maxFaceCount: Int = DEFAULT_MAX_FACE_COUNT
        private var recognitionThreshold: Float = DEFAULT_RECOGNITION_THRESHOLD
        private var featureVectorDimension: Int = DEFAULT_FEATURE_VECTOR_DIMENSION
        private var minFaceSize: Int = DEFAULT_MIN_FACE_SIZE
        private var maxFaceSize: Int = DEFAULT_MAX_FACE_SIZE
        private var enableDebugLog: Boolean = false
        private var faceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE
        private var databaseName: String = DEFAULT_DATABASE_NAME
        private var modelFileName: String = DEFAULT_MODEL_FILE_NAME
        private var similarityMethod: SimilarityMethod = SimilarityMethod.COSINE
        private var featureInputSize: Int = DEFAULT_FEATURE_INPUT_SIZE

        fun maxFaceCount(count: Int) = apply {
            require(count > 0) { "最大人脸数量必须大于0" }
            this.maxFaceCount = count
        }

        fun recognitionThreshold(threshold: Float) = apply {
            require(threshold in 0.0f..1.0f) { "识别阈值必须在0.0-1.0之间" }
            this.recognitionThreshold = threshold
        }

        fun featureVectorDimension(dimension: Int) = apply {
            require(dimension > 0) { "特征向量维度必须大于0" }
            this.featureVectorDimension = dimension
        }

        fun minFaceSize(size: Int) = apply {
            require(size > 0) { "最小人脸尺寸必须大于0" }
            this.minFaceSize = size
        }

        fun maxFaceSize(size: Int) = apply {
            require(size > 0) { "最大人脸尺寸必须大于0" }
            this.maxFaceSize = size
        }

        fun enableDebugLog(enable: Boolean) = apply {
            this.enableDebugLog = enable
        }

        fun faceDetectionConfidence(confidence: Float) = apply {
            require(confidence in 0.0f..1.0f) { "人脸检测置信度必须在0.0-1.0之间" }
            this.faceDetectionConfidence = confidence
        }

        fun databaseName(name: String) = apply {
            require(name.isNotBlank()) { "数据库名称不能为空" }
            this.databaseName = name
        }

        fun modelFileName(fileName: String) = apply {
            require(fileName.isNotBlank()) { "模型文件名不能为空" }
            this.modelFileName = fileName
        }

        fun similarityMethod(method: SimilarityMethod) = apply {
            this.similarityMethod = method
        }

        fun featureInputSize(featureInputSize: Int) = apply {
            require(featureInputSize < 0) { "输入大小必须大于0" }
            this.featureInputSize = featureInputSize
        }

        fun build(): FaceRecognitionConfig {
            require(maxFaceSize > minFaceSize) { "最大人脸尺寸必须大于最小人脸尺寸" }

            val config = FaceRecognitionConfig(
                maxFaceCount = maxFaceCount,
                recognitionThreshold = recognitionThreshold,
                featureVectorDimension = featureVectorDimension,
                minFaceSize = minFaceSize,
                maxFaceSize = maxFaceSize,
                enableDebugLog = enableDebugLog,
                faceDetectionConfidence = faceDetectionConfidence,
                databaseName = databaseName,
                modelFileName = modelFileName,
                similarityMethod = similarityMethod,
                featureInputSize = featureInputSize,
            )

            require(config.validate()) { "配置验证失败" }
            return config
        }
    }
}
