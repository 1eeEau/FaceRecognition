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
     * 比较两个人脸向量
     * @param vector1 第一个人脸向量
     * @param vector2 第二个人脸向量
     * @return 比较结果
     */
    fun compare(vector1: FaceVector, vector2: FaceVector): ComparisonResult {
        try {
            val similarity = when (config.similarityMethod) {
                FaceRecognitionConfig.SimilarityMethod.COSINE -> {
                    vector1.cosineSimilarity(vector2)
                }

                FaceRecognitionConfig.SimilarityMethod.EUCLIDEAN -> {
                    val distance = vector1.euclideanDistance(vector2)
                    VectorUtils.distanceToSimilarity(distance, 2.0f) // 最大距离设为2.0
                }

                FaceRecognitionConfig.SimilarityMethod.MANHATTAN -> {
                    val distance = vector1.manhattanDistance(vector2)
                    VectorUtils.distanceToSimilarity(distance, vector1.dimension.toFloat())
                }
            }

            val distance = when (config.similarityMethod) {
                FaceRecognitionConfig.SimilarityMethod.COSINE -> {
                    1f - similarity // 余弦距离
                }

                FaceRecognitionConfig.SimilarityMethod.EUCLIDEAN -> {
                    vector1.euclideanDistance(vector2)
                }

                FaceRecognitionConfig.SimilarityMethod.MANHATTAN -> {
                    vector1.manhattanDistance(vector2)
                }
            }

            val isMatch = similarity >= config.recognitionThreshold

            return ComparisonResult(
                similarity = similarity,
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
}
