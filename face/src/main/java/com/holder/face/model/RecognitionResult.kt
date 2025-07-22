package com.holder.face.model

/**
 * 人脸识别结果模型
 * 封装识别操作的结果信息
 */
data class RecognitionResult(
    /** 是否识别成功 */
    val isSuccess: Boolean,
    
    /** 识别到的人员ID */
    val personId: String? = null,
    
    /** 识别置信度 [0.0, 1.0] */
    val confidence: Float = 0f,
    
    /** 错误信息 */
    val errorMessage: String? = null,
    
    /** 处理耗时 (毫秒) */
    val processingTime: Long = 0L,
    
    /** 检测到的人脸数量 */
    val detectedFaceCount: Int = 0,
    
    /** 额外信息 */
    val extras: Map<String, Any> = emptyMap()
) {
    
    companion object {
        /**
         * 创建成功的识别结果
         */
        fun success(
            personId: String,
            confidence: Float,
            processingTime: Long = 0L,
            detectedFaceCount: Int = 1,
            extras: Map<String, Any> = emptyMap()
        ): RecognitionResult {
            return RecognitionResult(
                isSuccess = true,
                personId = personId,
                confidence = confidence,
                errorMessage = null,
                processingTime = processingTime,
                detectedFaceCount = detectedFaceCount,
                extras = extras
            )
        }
        
        /**
         * 创建失败的识别结果
         */
        fun failure(
            errorMessage: String,
            processingTime: Long = 0L,
            detectedFaceCount: Int = 0,
            extras: Map<String, Any> = emptyMap()
        ): RecognitionResult {
            return RecognitionResult(
                isSuccess = false,
                personId = null,
                confidence = 0f,
                errorMessage = errorMessage,
                processingTime = processingTime,
                detectedFaceCount = detectedFaceCount,
                extras = extras
            )
        }
        
        /**
         * 创建无匹配的识别结果
         */
        fun noMatch(
            processingTime: Long = 0L,
            detectedFaceCount: Int = 1,
            extras: Map<String, Any> = emptyMap()
        ): RecognitionResult {
            return RecognitionResult(
                isSuccess = false,
                personId = null,
                confidence = 0f,
                errorMessage = "未找到匹配的人脸",
                processingTime = processingTime,
                detectedFaceCount = detectedFaceCount,
                extras = extras
            )
        }
        
        /**
         * 创建未检测到人脸的结果
         */
        fun noFaceDetected(
            processingTime: Long = 0L,
            extras: Map<String, Any> = emptyMap()
        ): RecognitionResult {
            return RecognitionResult(
                isSuccess = false,
                personId = null,
                confidence = 0f,
                errorMessage = "未检测到人脸",
                processingTime = processingTime,
                detectedFaceCount = 0,
                extras = extras
            )
        }
        
        /**
         * 创建多人脸检测结果
         */
        fun multipleFacesDetected(
            detectedFaceCount: Int,
            processingTime: Long = 0L,
            extras: Map<String, Any> = emptyMap()
        ): RecognitionResult {
            return RecognitionResult(
                isSuccess = false,
                personId = null,
                confidence = 0f,
                errorMessage = "检测到多个人脸 ($detectedFaceCount 个)，请确保图像中只有一个人脸",
                processingTime = processingTime,
                detectedFaceCount = detectedFaceCount,
                extras = extras
            )
        }
    }
    
    /**
     * 获取格式化的结果描述
     */
    fun getDescription(): String {
        return when {
            isSuccess -> "识别成功: $personId (置信度: ${String.format("%.2f", confidence)})"
            errorMessage != null -> "识别失败: $errorMessage"
            else -> "未知错误"
        }
    }
    
    /**
     * 检查置信度是否达到指定阈值
     */
    fun isConfidenceAbove(threshold: Float): Boolean {
        return confidence >= threshold
    }
    
    /**
     * 获取处理时间的格式化字符串
     */
    fun getFormattedProcessingTime(): String {
        return when {
            processingTime < 1000 -> "${processingTime}ms"
            processingTime < 60000 -> "${String.format("%.1f", processingTime / 1000.0)}s"
            else -> "${String.format("%.1f", processingTime / 60000.0)}min"
        }
    }
    
    /**
     * 添加额外信息
     */
    fun withExtra(key: String, value: Any): RecognitionResult {
        val newExtras = extras.toMutableMap()
        newExtras[key] = value
        return copy(extras = newExtras)
    }
    
    /**
     * 获取额外信息
     */
    inline fun <reified T> getExtra(key: String): T? {
        return extras[key] as? T
    }
    
    /**
     * 转换为JSON格式的字符串 (简单实现)
     */
    fun toJsonString(): String {
        return buildString {
            append("{")
            append("\"isSuccess\":$isSuccess,")
            append("\"personId\":${if (personId != null) "\"$personId\"" else "null"},")
            append("\"confidence\":$confidence,")
            append("\"errorMessage\":${if (errorMessage != null) "\"$errorMessage\"" else "null"},")
            append("\"processingTime\":$processingTime,")
            append("\"detectedFaceCount\":$detectedFaceCount")
            append("}")
        }
    }
}
