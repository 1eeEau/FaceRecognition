package com.holder.face.model

import com.holder.face.utils.VectorUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import kotlin.random.Random

/**
 * 人脸向量数据模型
 * 封装人脸特征向量及相关操作
 */
data class FaceVector(
    /** 人员ID (后端返回的ID) */
    val personId: String,

    /** 特征向量 */
    val vector: FloatArray,

    /** 创建时间 */
    val createdTime: Date = Date(),

    /** 置信度 (可选) */
    val confidence: Float? = null,
    /** 原始的数据库人脸信息 */
    val originFaceEntity: FaceEntity? = null
) {

    /** 向量维度 */
    val dimension: Int get() = vector.size

    companion object {
        /**
         * 创建零向量
         */
        fun zeros(personId: String, dimension: Int): FaceVector {
            return FaceVector(personId, FloatArray(dimension) { 0.0f })
        }

        /**
         * 创建随机向量 (用于测试)
         */
        fun random(personId: String, dimension: Int): FaceVector {
            val vector = FloatArray(dimension) { Random.nextFloat() * 2 - 1 } // [-1, 1]
            return FaceVector(personId, vector)
        }

        /**
         * 从字节数组恢复向量
         */
        fun fromByteArray(
            personId: String,
            byteArray: ByteArray,
            createdTime: Date = Date()
        ): FaceVector {
            val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
            val dimension = byteArray.size / 4 // 每个float占4字节
            val vector = FloatArray(dimension) { buffer.float }
            return FaceVector(personId, vector, createdTime)
        }
    }

    /**
     * 转换为字节数组 (用于数据库存储)
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * 计算与另一个向量的余弦相似度
     * @param other 另一个人脸向量
     * @return 相似度值 [0, 1]
     */
    fun cosineSimilarity(other: FaceVector): Float {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        return VectorUtils.cosineSimilarity(this.vector, other.vector)
    }

    /**
     * 计算与另一个向量的欧几里得距离
     */
    fun euclideanDistance(other: FaceVector): Float {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        return VectorUtils.euclideanDistance(this.vector, other.vector)
    }

    /**
     * 计算与另一个向量的曼哈顿距离
     */
    fun manhattanDistance(other: FaceVector): Float {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        return VectorUtils.manhattanDistance(this.vector, other.vector)
    }

    /**
     * 向量归一化
     */
    fun normalize(): FaceVector {
        val normalizedVector = VectorUtils.normalize(this.vector)
        return this.copy(vector = normalizedVector)
    }

    /**
     * 计算向量的L2范数
     */
    fun l2Norm(): Float {
        return VectorUtils.l2Norm(this.vector)
    }

    /**
     * 计算向量的L1范数
     */
    fun l1Norm(): Float {
        return VectorUtils.l1Norm(this.vector)
    }

    /**
     * 检查向量是否已归一化
     */
    fun isNormalized(tolerance: Float = 1e-6f): Boolean {
        return kotlin.math.abs(l2Norm() - 1.0f) < tolerance
    }

    /**
     * 向量加法
     */
    operator fun plus(other: FaceVector): FaceVector {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        val resultVector = FloatArray(dimension) { i ->
            this.vector[i] + other.vector[i]
        }
        return FaceVector("${this.personId}_plus_${other.personId}", resultVector)
    }

    /**
     * 向量减法
     */
    operator fun minus(other: FaceVector): FaceVector {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        val resultVector = FloatArray(dimension) { i ->
            this.vector[i] - other.vector[i]
        }
        return FaceVector("${this.personId}_minus_${other.personId}", resultVector)
    }

    /**
     * 标量乘法
     */
    operator fun times(scalar: Float): FaceVector {
        val resultVector = FloatArray(dimension) { i ->
            this.vector[i] * scalar
        }
        return this.copy(vector = resultVector)
    }

    /**
     * 向量点积
     */
    fun dot(other: FaceVector): Float {
        require(this.dimension == other.dimension) {
            "向量维度不匹配: ${this.dimension} vs ${other.dimension}"
        }
        return this.vector.zip(other.vector) { a, b -> a * b }.sum()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceVector

        if (personId != other.personId) return false
        if (!vector.contentEquals(other.vector)) return false
        if (createdTime != other.createdTime) return false
        if (confidence != other.confidence) return false

        return true
    }

    override fun hashCode(): Int {
        var result = personId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + createdTime.hashCode()
        result = 31 * result + (confidence?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FaceVector(personId='$personId', dimension=$dimension, " +
                "createdTime=$createdTime, confidence=$confidence)"
    }
}
