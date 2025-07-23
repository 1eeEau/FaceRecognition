package com.holder.face.core

import android.util.Log
import com.holder.face.config.FaceRecognitionConfig
import com.holder.face.exception.FaceRecognitionException
import com.holder.face.model.FaceVector
import com.holder.face.model.RecognitionResult
import com.holder.face.utils.VectorUtils

/**
 * 人脸比较器
 * 负责人脸特征向量的比较和匹配
 */
class FaceComparator(private val config: FaceRecognitionConfig) {

    /**
     * 比较结果数据类
     */
    data class ComparisonResult(
        val similarity: Float,
        val distance: Float,
        val isMatch: Boolean,
        val personId: String,
        val method: String
    ) {
        fun toRecognitionResult(processingTime: Long = 0L): RecognitionResult {
            return if (isMatch) {
                RecognitionResult.success(
                    personId = personId,
                    confidence = similarity,
                    processingTime = processingTime,
                    extras = mapOf(
                        "distance" to distance,
                        "method" to method
                    )
                )
            } else {
                RecognitionResult.noMatch(
                    processingTime = processingTime,
                    extras = mapOf(
                        "bestSimilarity" to similarity,
                        "bestPersonId" to personId,
                        "method" to method
                    )
                )
            }
        }
    }

    /**
     * 批量比较结果
     */
    data class BatchComparisonResult(
        val results: List<ComparisonResult>,
        val bestMatch: ComparisonResult?,
        val processingTime: Long
    ) {
        val hasMatch: Boolean get() = bestMatch?.isMatch == true
        val matchCount: Int get() = results.count { it.isMatch }
    }

    /**
     * 比较两个人脸向量 (增强版本)
     * @param vector1 第一个人脸向量
     * @param vector2 第二个人脸向量
     * @return 比较结果
     */
    fun compare(vector1: FaceVector, vector2: FaceVector): ComparisonResult {
        try {
            // 1. 预验证：检查向量质量
            if (!isValidVector(vector1) || !isValidVector(vector2)) {
                return ComparisonResult(
                    similarity = 0f,
                    distance = Float.MAX_VALUE,
                    isMatch = false,
                    personId = vector2.personId,
                    method = "${config.similarityMethod.name}_INVALID"
                )
            }

            // 2. 计算原始相似度
            val rawSimilarity = when (config.similarityMethod) {
                FaceRecognitionConfig.SimilarityMethod.COSINE -> {
                    calculateEnhancedCosineSimilarity(vector1, vector2)
                }

                FaceRecognitionConfig.SimilarityMethod.EUCLIDEAN -> {
                    val distance = vector1.euclideanDistance(vector2)
                    VectorUtils.distanceToSimilarity(distance, 2.0f)
                }

                FaceRecognitionConfig.SimilarityMethod.MANHATTAN -> {
                    val distance = vector1.manhattanDistance(vector2)
                    VectorUtils.distanceToSimilarity(distance, vector1.dimension.toFloat())
                }
            }

            // 3. 计算距离
            val distance = when (config.similarityMethod) {
                FaceRecognitionConfig.SimilarityMethod.COSINE -> {
                    1f - rawSimilarity
                }

                FaceRecognitionConfig.SimilarityMethod.EUCLIDEAN -> {
                    vector1.euclideanDistance(vector2)
                }

                FaceRecognitionConfig.SimilarityMethod.MANHATTAN -> {
                    vector1.manhattanDistance(vector2)
                }
            }

            // 4. 质量加权调整
            val qualityWeight = calculateVectorQualityWeight(vector1, vector2)
            val adjustedSimilarity = rawSimilarity * qualityWeight

            // 5. 基础阈值判断
            val isMatch = adjustedSimilarity >= config.recognitionThreshold

            if (config.enableDebugLog) {
                Log.d("FaceComparator", "向量比较详情:")
                Log.d("FaceComparator", "  原始相似度: $rawSimilarity")
                Log.d("FaceComparator", "  质量权重: $qualityWeight")
                Log.d("FaceComparator", "  调整后相似度: $adjustedSimilarity")
                Log.d("FaceComparator", "  距离: $distance")
                Log.d("FaceComparator", "  匹配结果: $isMatch")
            }

            return ComparisonResult(
                similarity = adjustedSimilarity,
                distance = distance,
                isMatch = isMatch,
                personId = vector2.personId,
                method = config.similarityMethod.name
            )
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "人脸向量比较失败", e
            )
        }
    }

    /**
     * 在候选人脸中找到最匹配的
     * @param targetVector 目标人脸向量
     * @param candidates 候选人脸向量列表
     * @return 最佳匹配结果
     */
    fun findBestMatch(
        targetVector: FaceVector,
        candidates: List<FaceVector>
    ): ComparisonResult? {
        if (candidates.isEmpty()) return null

        val startTime = System.currentTimeMillis()

        try {
            val results = candidates.map { candidate ->
                compare(targetVector, candidate)
            }

            val bestResult = results.maxByOrNull { it.similarity }
            val processingTime = System.currentTimeMillis() - startTime

            if (config.enableDebugLog) {
                Log.i("FaceComparator", "最佳匹配查找完成: ${processingTime}ms")
                Log.i("FaceComparator", "候选数量: ${candidates.size}")
                Log.i("FaceComparator", "最佳相似度: ${bestResult?.similarity}")
            }

            return bestResult
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "最佳匹配查找失败", e
            )
        }
    }

    /**
     * 批量比较，返回所有匹配结果
     * @param targetVector 目标人脸向量
     * @param candidates 候选人脸向量列表
     * @param returnAll 是否返回所有结果，false时只返回匹配的结果
     * @return 批量比较结果
     */
    fun batchCompare(
        targetVector: FaceVector,
        candidates: List<FaceVector>,
        returnAll: Boolean = false
    ): BatchComparisonResult {
        val startTime = System.currentTimeMillis()

        try {
            val allResults = candidates.map { candidate ->
                compare(targetVector, candidate)
            }

            val filteredResults = if (returnAll) {
                allResults
            } else {
                allResults.filter { it.isMatch }
            }

            val bestMatch = allResults.maxByOrNull { it.similarity }
            val processingTime = System.currentTimeMillis() - startTime

            if (config.enableDebugLog) {
                Log.i("FaceComparator", "批量比较完成: ${processingTime}ms")
                Log.i("FaceComparator", "候选数量: ${candidates.size}")
                Log.i("FaceComparator", "匹配数量: ${filteredResults.count { it.isMatch }}")
            }

            return BatchComparisonResult(
                results = filteredResults.sortedByDescending { it.similarity },
                bestMatch = bestMatch,
                processingTime = processingTime
            )
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "批量比较失败", e
            )
        }
    }

    /**
     * 获取前N个最相似的结果
     * @param targetVector 目标人脸向量
     * @param candidates 候选人脸向量列表
     * @param topN 返回的数量
     * @return 按相似度降序排列的结果列表
     */
    fun getTopMatches(
        targetVector: FaceVector,
        candidates: List<FaceVector>,
        topN: Int = 5
    ): List<ComparisonResult> {
        try {
            val results = candidates.map { candidate ->
                compare(targetVector, candidate)
            }

            return results
                .sortedByDescending { it.similarity }
                .take(topN)
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "获取前N个匹配失败", e
            )
        }
    }

    /**
     * 验证人脸匹配 (1:1验证)
     * @param vector1 第一个人脸向量
     * @param vector2 第二个人脸向量
     * @return 是否为同一人
     */
    fun verify(vector1: FaceVector, vector2: FaceVector): Boolean {
        val result = compare(vector1, vector2)
        return result.isMatch
    }

    /**
     * 计算相似度分布统计
     * @param targetVector 目标人脸向量
     * @param candidates 候选人脸向量列表
     * @return 统计信息
     */
    fun calculateSimilarityStats(
        targetVector: FaceVector,
        candidates: List<FaceVector>
    ): SimilarityStats {
        if (candidates.isEmpty()) {
            return SimilarityStats(0, 0f, 0f, 0f, 0f, 0f)
        }

        try {
            val similarities = candidates.map { candidate ->
                compare(targetVector, candidate).similarity
            }

            val count = similarities.size
            val mean = similarities.average().toFloat()
            val max = similarities.maxOrNull() ?: 0f
            val min = similarities.minOrNull() ?: 0f
            val variance = similarities.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance)

            return SimilarityStats(count, mean, max, min, variance, stdDev)
        } catch (e: Exception) {
            throw FaceRecognitionException.VectorCalculationException(
                "相似度统计计算失败", e
            )
        }
    }

    /**
     * 相似度统计信息
     */
    data class SimilarityStats(
        val count: Int,
        val mean: Float,
        val max: Float,
        val min: Float,
        val variance: Float,
        val stdDev: Float
    ) {
        fun getDescription(): String {
            return "统计信息: 数量=$count, 平均=${String.format("%.3f", mean)}, " +
                    "最大=${String.format("%.3f", max)}, 最小=${String.format("%.3f", min)}, " +
                    "标准差=${String.format("%.3f", stdDev)}"
        }
    }

    /**
     * 设置动态阈值 (基于候选向量的分布)
     * @param candidates 候选人脸向量列表
     * @return 建议的识别阈值
     */
    fun calculateDynamicThreshold(candidates: List<FaceVector>): Float {
        if (candidates.size < 2) {
            return config.recognitionThreshold
        }

        try {
            // 计算候选向量之间的相似度分布
            val similarities = mutableListOf<Float>()

            for (i in candidates.indices) {
                for (j in i + 1 until candidates.size) {
                    val similarity = compare(candidates[i], candidates[j]).similarity
                    similarities.add(similarity)
                }
            }

            if (similarities.isEmpty()) {
                return config.recognitionThreshold
            }

            val mean = similarities.average().toFloat()
            val stdDev = kotlin.math.sqrt(
                similarities.map { (it - mean) * (it - mean) }.average().toFloat()
            )

            // 动态阈值 = 平均值 + 2倍标准差
            val dynamicThreshold = mean + 2 * stdDev

            // 限制在合理范围内
            return kotlin.math.max(
                config.recognitionThreshold,
                kotlin.math.min(0.95f, dynamicThreshold)
            )
        } catch (e: Exception) {
            return config.recognitionThreshold
        }
    }

    /**
     * 增强的余弦相似度计算
     * 使用简化的映射方式，避免过度复杂的分段映射
     */
    private fun calculateEnhancedCosineSimilarity(vector1: FaceVector, vector2: FaceVector): Float {
        try {
            // 1. 计算原始余弦相似度 (范围 [-1, 1])
            val dotProduct = vector1.dot(vector2)
            val norm1 = vector1.l2Norm()
            val norm2 = vector2.l2Norm()

            if (norm1 == 0f || norm2 == 0f) {
                return 0f
            }

            val rawCosine = dotProduct / (norm1 * norm2)

            // 2. 简化的映射方式：线性映射到 [0, 1]
            // 对于人脸识别，通常余弦相似度在 [0, 1] 范围内更有意义
            val similarity = kotlin.math.max(0f, rawCosine)

            if (config.enableDebugLog) {
                Log.d("FaceComparator", "余弦相似度计算: 原始=$rawCosine, 映射后=$similarity")
            }

            return similarity
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.w("FaceComparator", "余弦相似度计算失败: ${e.message}")
            }
            return 0f
        }
    }

    /**
     * 验证向量是否有效
     */
    private fun isValidVector(vector: FaceVector): Boolean {
        try {
            // 1. 检查向量维度
            if (vector.dimension != config.featureVectorDimension) {
                if (config.enableDebugLog) {
                    Log.w("FaceComparator", "向量维度不匹配: ${vector.dimension} vs ${config.featureVectorDimension}")
                }
                return false
            }

            // 2. 检查是否包含无效值
            if (vector.vector.any { it.isNaN() || it.isInfinite() }) {
                if (config.enableDebugLog) {
                    Log.w("FaceComparator", "向量包含无效值 (NaN或Infinite)")
                }
                return false
            }

            // 3. 检查是否为零向量
            val norm = vector.l2Norm()
            if (norm < 1e-6f) {
                if (config.enableDebugLog) {
                    Log.w("FaceComparator", "向量接近零向量，范数: $norm")
                }
                return false
            }

            // 4. 检查向量是否归一化
            if (kotlin.math.abs(norm - 1.0f) > 0.1f) {
                if (config.enableDebugLog) {
                    Log.w("FaceComparator", "向量未正确归一化，范数: $norm")
                }
                // 注意：这里不返回false，因为可能是正常的未归一化向量
            }

            return true
        } catch (e: Exception) {
            if (config.enableDebugLog) {
                Log.w("FaceComparator", "向量验证失败: ${e.message}")
            }
            return false
        }
    }

    /**
     * 计算向量质量权重
     */
    private fun calculateVectorQualityWeight(vector1: FaceVector, vector2: FaceVector): Float {
        try {
            var weight = 1.0f

            // 1. 基于置信度的权重
            val conf1 = vector1.confidence ?: 0.8f
            val conf2 = vector2.confidence ?: 0.8f
            val confidenceWeight = (conf1 + conf2) / 2f

            // 2. 基于向量归一化程度的权重
            val norm1 = vector1.l2Norm()
            val norm2 = vector2.l2Norm()
            val normWeight = kotlin.math.min(
                1f - kotlin.math.abs(norm1 - 1f),
                1f - kotlin.math.abs(norm2 - 1f)
            )

            // 3. 基于特征分布的权重
            val distWeight1 = calculateFeatureDistributionWeight(vector1.vector)
            val distWeight2 = calculateFeatureDistributionWeight(vector2.vector)
            val distributionWeight = (distWeight1 + distWeight2) / 2f

            // 综合权重 (范围 [0.5, 1.0])
            weight = 0.5f + (confidenceWeight * 0.2f + normWeight * 0.2f + distributionWeight * 0.1f)

            return kotlin.math.max(0.5f, kotlin.math.min(1.0f, weight))
        } catch (e: Exception) {
            return 0.8f
        }
    }

    /**
     * 计算特征分布权重
     */
    private fun calculateFeatureDistributionWeight(vector: FloatArray): Float {
        try {
            val mean = vector.average().toFloat()
            val variance = vector.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance)

            // 良好的特征分布应该有适中的标准差 (0.1 - 0.5)
            return when {
                stdDev < 0.05f -> 0.3f  // 方差过小，特征不够丰富
                stdDev < 0.1f -> 0.6f   // 方差较小
                stdDev <= 0.5f -> 1.0f  // 理想范围
                stdDev <= 1.0f -> 0.8f  // 方差较大
                else -> 0.4f            // 方差过大，可能有噪声
            }
        } catch (e: Exception) {
            return 0.8f
        }
    }
}
