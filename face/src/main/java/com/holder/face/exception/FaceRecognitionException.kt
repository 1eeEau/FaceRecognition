package com.holder.face.exception

/**
 * 人脸识别异常基类
 */
sealed class FaceRecognitionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * 配置异常
     */
    class ConfigurationException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("配置错误: $message", cause)
    
    /**
     * 初始化异常
     */
    class InitializationException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("初始化失败: $message", cause)
    
    /**
     * 人脸检测异常
     */
    class FaceDetectionException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("人脸检测失败: $message", cause)
    
    /**
     * 特征提取异常
     */
    class FeatureExtractionException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("特征提取失败: $message", cause)
    
    /**
     * 数据库操作异常
     */
    class DatabaseException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("数据库操作失败: $message", cause)
    
    /**
     * 存储空间不足异常
     */
    class StorageFullException(
        maxCount: Int
    ) : FaceRecognitionException("存储空间已满，最大支持 $maxCount 个人脸")
    
    /**
     * 人脸不存在异常
     */
    class FaceNotFoundException(
        personId: String
    ) : FaceRecognitionException("未找到人员ID为 $personId 的人脸数据")
    
    /**
     * 模型加载异常
     */
    class ModelLoadException(
        modelPath: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("模型加载失败: $modelPath", cause)
    
    /**
     * 图像处理异常
     */
    class ImageProcessingException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("图像处理失败: $message", cause)
    
    /**
     * 向量计算异常
     */
    class VectorCalculationException(
        message: String,
        cause: Throwable? = null
    ) : FaceRecognitionException("向量计算失败: $message", cause)
}
