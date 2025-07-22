package com.holder.face.utils

import com.holder.face.exception.FaceRecognitionException
import kotlin.math.*

/**
 * 向量计算工具类
 * 提供各种向量运算和相似度计算方法
 */
object VectorUtils {
    
    /**
     * 计算两个向量的余弦相似度
     * @param vector1 第一个向量
     * @param vector2 第二个向量
     * @return 相似度值 [0, 1]，1表示完全相同，0表示完全不同
     */
    fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        require(vector1.size == vector2.size) { 
            "向量维度不匹配: ${vector1.size} vs ${vector2.size}" 
        }
        
        try {
            val dotProduct = dotProduct(vector1, vector2)
            val norm1 = l2Norm(vector1)
            val norm2 = l2Norm(vector2)
            
            if (norm1 == 0f || norm2 == 0f) {
                return 0f
            }
            
            // 余弦相似度范围是[-1, 1]，映射到[0, 1]
            val cosineSim = dotProduct / (norm1 * norm2)
            return (cosineSim + 1f) / 2f
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "余弦相似度计算失败", e
            )
        }
    }
    
    /**
     * 计算两个向量的欧几里得距离
     */
    fun euclideanDistance(vector1: FloatArray, vector2: FloatArray): Float {
        require(vector1.size == vector2.size) { 
            "向量维度不匹配: ${vector1.size} vs ${vector2.size}" 
        }
        
        try {
            var sum = 0f
            for (i in vector1.indices) {
                val diff = vector1[i] - vector2[i]
                sum += diff * diff
            }
            return sqrt(sum)
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "欧几里得距离计算失败", e
            )
        }
    }
    
    /**
     * 计算两个向量的曼哈顿距离
     */
    fun manhattanDistance(vector1: FloatArray, vector2: FloatArray): Float {
        require(vector1.size == vector2.size) { 
            "向量维度不匹配: ${vector1.size} vs ${vector2.size}" 
        }
        
        try {
            var sum = 0f
            for (i in vector1.indices) {
                sum += abs(vector1[i] - vector2[i])
            }
            return sum
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "曼哈顿距离计算失败", e
            )
        }
    }
    
    /**
     * 向量归一化 (L2归一化)
     */
    fun normalize(vector: FloatArray): FloatArray {
        try {
            val norm = l2Norm(vector)
            if (norm == 0f) {
                return vector.clone()
            }
            
            return FloatArray(vector.size) { i ->
                vector[i] / norm
            }
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "向量归一化失败", e
            )
        }
    }
    
    /**
     * 计算向量的L2范数 (欧几里得范数)
     */
    fun l2Norm(vector: FloatArray): Float {
        try {
            var sum = 0f
            for (value in vector) {
                sum += value * value
            }
            return sqrt(sum)
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "L2范数计算失败", e
            )
        }
    }
    
    /**
     * 计算向量的L1范数 (曼哈顿范数)
     */
    fun l1Norm(vector: FloatArray): Float {
        try {
            var sum = 0f
            for (value in vector) {
                sum += abs(value)
            }
            return sum
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "L1范数计算失败", e
            )
        }
    }
    
    /**
     * 计算两个向量的点积
     */
    fun dotProduct(vector1: FloatArray, vector2: FloatArray): Float {
        require(vector1.size == vector2.size) { 
            "向量维度不匹配: ${vector1.size} vs ${vector2.size}" 
        }
        
        try {
            var sum = 0f
            for (i in vector1.indices) {
                sum += vector1[i] * vector2[i]
            }
            return sum
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "点积计算失败", e
            )
        }
    }
    
    /**
     * 在候选向量中找到与目标向量最相似的一个
     * @param target 目标向量
     * @param candidates 候选向量列表
     * @return Pair<索引, 相似度> 或 null (如果没有候选向量)
     */
    fun findMostSimilar(
        target: FloatArray, 
        candidates: List<FloatArray>
    ): Pair<Int, Float>? {
        if (candidates.isEmpty()) return null
        
        var bestIndex = 0
        var bestSimilarity = cosineSimilarity(target, candidates[0])
        
        for (i in 1 until candidates.size) {
            val similarity = cosineSimilarity(target, candidates[i])
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = i
            }
        }
        
        return Pair(bestIndex, bestSimilarity)
    }
    
    /**
     * 在候选向量中找到与目标向量最相似的前N个
     * @param target 目标向量
     * @param candidates 候选向量列表
     * @param topN 返回的数量
     * @return 按相似度降序排列的结果列表 List<Pair<索引, 相似度>>
     */
    fun findTopSimilar(
        target: FloatArray, 
        candidates: List<FloatArray>,
        topN: Int
    ): List<Pair<Int, Float>> {
        if (candidates.isEmpty() || topN <= 0) return emptyList()
        
        val similarities = candidates.mapIndexed { index, candidate ->
            Pair(index, cosineSimilarity(target, candidate))
        }
        
        return similarities
            .sortedByDescending { it.second }
            .take(topN)
    }
    
    /**
     * 将距离值转换为相似度值
     * @param distance 距离值
     * @param maxDistance 最大距离 (用于归一化)
     * @return 相似度值 [0, 1]
     */
    fun distanceToSimilarity(distance: Float, maxDistance: Float = 1f): Float {
        return max(0f, 1f - distance / maxDistance)
    }
    
    /**
     * 检查向量是否有效 (不包含NaN或无穷大)
     */
    fun isValidVector(vector: FloatArray): Boolean {
        return vector.all { value ->
            value.isFinite() && !value.isNaN()
        }
    }
    
    /**
     * 向量标准化 (零均值，单位方差)
     */
    fun standardize(vector: FloatArray): FloatArray {
        try {
            val mean = vector.average().toFloat()
            val variance = vector.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = sqrt(variance)
            
            if (stdDev == 0f) {
                return vector.clone()
            }
            
            return FloatArray(vector.size) { i ->
                (vector[i] - mean) / stdDev
            }
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "向量标准化失败", e
            )
        }
    }
    
    /**
     * 计算向量间的批量相似度
     * @param target 目标向量
     * @param candidates 候选向量列表
     * @return 相似度列表
     */
    fun batchSimilarity(
        target: FloatArray,
        candidates: List<FloatArray>
    ): List<Float> {
        return candidates.map { candidate ->
            cosineSimilarity(target, candidate)
        }
    }
}
