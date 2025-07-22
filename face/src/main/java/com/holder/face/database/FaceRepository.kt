package com.holder.face.database

import com.holder.face.config.FaceRecognitionConfig
import com.holder.face.exception.FaceRecognitionException
import com.holder.face.model.FaceEntity
import com.holder.face.model.FaceVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * 人脸数据仓库
 * 封装数据库操作，提供业务层接口
 */
class FaceRepository(
    private val faceDao: FaceDao,
    private val config: FaceRecognitionConfig
) {
    
    /**
     * 添加人脸数据
     * @param faceVector 人脸向量
     * @param remarks 备注信息
     * @param faceImageBase64 人脸图片Base64编码 (可选)
     * @return 插入的记录ID
     * @throws FaceRecognitionException.StorageFullException 存储空间已满
     * @throws FaceRecognitionException.DatabaseException 数据库操作失败
     */
    suspend fun addFace(
        faceVector: FaceVector,
        remarks: String? = null,
        faceImageBase64: String? = null
    ): Long {
        try {
            // 检查存储空间
            val currentCount = faceDao.getEnabledFaceCount()
            if (currentCount >= config.maxFaceCount) {
                throw FaceRecognitionException.StorageFullException(config.maxFaceCount)
            }

            // 检查人员ID是否已存在
            if (faceDao.isPersonIdExists(faceVector.personId)) {
                // 更新现有记录
                return updateFace(faceVector, remarks, faceImageBase64)
            }

            // 插入新记录
            val faceEntity = FaceEntity.fromFaceVector(faceVector, remarks, true, faceImageBase64)
            return faceDao.insertFace(faceEntity)
        } catch (e: FaceRecognitionException) {
            throw e
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("添加人脸数据失败", e)
        }
    }
    
    /**
     * 批量添加人脸数据
     * @param faceVectors 人脸向量列表
     * @param faceImagesBase64 对应的人脸图片Base64列表 (可选，长度应与faceVectors一致)
     */
    suspend fun addFaces(
        faceVectors: List<FaceVector>,
        faceImagesBase64: List<String?>? = null
    ): List<Long> {
        try {
            val currentCount = faceDao.getEnabledFaceCount()
            if (currentCount + faceVectors.size > config.maxFaceCount) {
                throw FaceRecognitionException.StorageFullException(config.maxFaceCount)
            }

            val faceEntities = faceVectors.mapIndexed { index, faceVector ->
                val imageBase64 = faceImagesBase64?.getOrNull(index)
                FaceEntity.fromFaceVector(faceVector, null, true, imageBase64)
            }
            return faceDao.insertFaces(faceEntities)
        } catch (e: FaceRecognitionException) {
            throw e
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("批量添加人脸数据失败", e)
        }
    }
    
    /**
     * 更新人脸数据
     * @param faceVector 人脸向量
     * @param remarks 备注信息
     * @param faceImageBase64 人脸图片Base64编码 (可选)
     */
    suspend fun updateFace(
        faceVector: FaceVector,
        remarks: String? = null,
        faceImageBase64: String? = null
    ): Long {
        try {
            val existingFace = faceDao.getFaceByPersonId(faceVector.personId)
                ?: throw FaceRecognitionException.FaceNotFoundException(faceVector.personId)

            var updatedFace = existingFace.updateVector(faceVector).updateRemarks(remarks)

            // 如果提供了新的图片，也更新图片
            if (faceImageBase64 != null) {
                updatedFace = updatedFace.updateFaceImage(faceImageBase64)
            }

            faceDao.updateFace(updatedFace)
            return updatedFace.id
        } catch (e: FaceRecognitionException) {
            throw e
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("更新人脸数据失败", e)
        }
    }
    
    /**
     * 删除人脸数据
     */
    suspend fun deleteFace(personId: String): Boolean {
        try {
            val deletedCount = faceDao.deleteFaceByPersonId(personId)
            return deletedCount > 0
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("删除人脸数据失败", e)
        }
    }
    
    /**
     * 获取人脸数据
     */
    suspend fun getFace(personId: String): FaceVector? {
        try {
            return faceDao.getFaceByPersonId(personId)?.toFaceVector()
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取人脸数据失败", e)
        }
    }
    
    /**
     * 获取所有启用的人脸数据
     */
    suspend fun getAllEnabledFaces(): List<FaceVector> {
        try {
            return faceDao.getAllEnabledFaces().map { it.toFaceVector() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取人脸数据列表失败", e)
        }
    }
    
    /**
     * 获取人脸数据流 (用于实时更新)
     */
    fun getAllEnabledFacesFlow(): Flow<List<FaceVector>> {
        return faceDao.getAllEnabledFacesFlow().map { entities ->
            entities.map { it.toFaceVector() }
        }
    }
    
    /**
     * 获取人脸数据总数
     */
    suspend fun getFaceCount(): Int {
        try {
            return faceDao.getEnabledFaceCount()
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取人脸数据总数失败", e)
        }
    }
    
    /**
     * 检查存储空间是否已满
     */
    suspend fun isStorageFull(): Boolean {
        try {
            val currentCount = faceDao.getEnabledFaceCount()
            return currentCount >= config.maxFaceCount
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * 获取剩余存储空间
     */
    suspend fun getRemainingCapacity(): Int {
        try {
            val currentCount = faceDao.getEnabledFaceCount()
            return maxOf(0, config.maxFaceCount - currentCount)
        } catch (e: Exception) {
            return 0
        }
    }
    
    /**
     * 启用/禁用人脸数据
     */
    suspend fun setFaceEnabled(personId: String, enabled: Boolean): Boolean {
        try {
            val updatedCount = faceDao.setFaceEnabled(personId, enabled)
            return updatedCount > 0
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("设置人脸状态失败", e)
        }
    }
    
    /**
     * 更新人脸备注
     */
    suspend fun updateFaceRemarks(personId: String, remarks: String?): Boolean {
        try {
            val updatedCount = faceDao.updateFaceRemarks(personId, remarks)
            return updatedCount > 0
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("更新人脸备注失败", e)
        }
    }
    
    /**
     * 搜索人脸数据
     */
    suspend fun searchFaces(keyword: String): List<FaceVector> {
        try {
            return faceDao.searchFaces(keyword).map { it.toFaceVector() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("搜索人脸数据失败", e)
        }
    }
    
    /**
     * 获取最近添加的人脸数据
     */
    suspend fun getRecentFaces(limit: Int = 10): List<FaceVector> {
        try {
            return faceDao.getRecentFaces(limit).map { it.toFaceVector() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取最近人脸数据失败", e)
        }
    }
    
    /**
     * 获取高置信度的人脸数据
     */
    suspend fun getHighConfidenceFaces(minConfidence: Float = 0.8f): List<FaceVector> {
        try {
            return faceDao.getHighConfidenceFaces(minConfidence).map { it.toFaceVector() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取高置信度人脸数据失败", e)
        }
    }
    
    /**
     * 清理存储空间 (删除最旧的数据)
     */
    suspend fun cleanupStorage(keepCount: Int = config.maxFaceCount): Int {
        try {
            val currentCount = faceDao.getEnabledFaceCount()
            if (currentCount <= keepCount) {
                return 0
            }
            
            val deleteCount = currentCount - keepCount
            val oldestFaces = faceDao.getOldestFaces(deleteCount)
            
            var deletedCount = 0
            for (face in oldestFaces) {
                deletedCount += faceDao.deleteFace(face)
            }
            
            return deletedCount
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("清理存储空间失败", e)
        }
    }
    
    /**
     * 清理过期数据
     */
    suspend fun cleanupExpiredData(beforeTime: Date): Int {
        try {
            return faceDao.cleanupOldData(beforeTime)
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("清理过期数据失败", e)
        }
    }
    
    /**
     * 获取数据库统计信息
     */
    suspend fun getDatabaseStats(): DatabaseStats {
        try {
            return faceDao.getDatabaseStats()
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取数据库统计信息失败", e)
        }
    }
    
    /**
     * 清空所有数据
     */
    suspend fun clearAllData(): Int {
        try {
            return faceDao.deleteAllFaces()
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("清空数据失败", e)
        }
    }
    
    /**
     * 检查人员ID是否存在
     */
    suspend fun isPersonIdExists(personId: String): Boolean {
        try {
            return faceDao.isPersonIdExists(personId)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 更新人脸图片
     * @param personId 人员ID
     * @param faceImageBase64 新的人脸图片Base64编码
     * @return 是否更新成功
     */
    suspend fun updateFaceImage(personId: String, faceImageBase64: String?): Boolean {
        try {
            val existingFace = faceDao.getFaceByPersonId(personId)
                ?: throw FaceRecognitionException.FaceNotFoundException(personId)

            val updatedFace = existingFace.updateFaceImage(faceImageBase64)
            faceDao.updateFace(updatedFace)
            return true
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("更新人脸图片失败", e)
        }
    }

    /**
     * 获取人脸图片
     * @param personId 人员ID
     * @return 人脸图片Base64编码，如果不存在返回null
     */
    suspend fun getFaceImage(personId: String): String? {
        try {
            return faceDao.getFaceByPersonId(personId)?.faceImageBase64
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取人脸图片失败", e)
        }
    }

    /**
     * 获取所有有图片的人脸数据
     */
    suspend fun getFacesWithImages(): List<FaceEntity> {
        try {
            return faceDao.getAllEnabledFaces().filter { it.hasFaceImage() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取有图片的人脸数据失败", e)
        }
    }

    /**
     * 获取没有图片的人脸数据
     */
    suspend fun getFacesWithoutImages(): List<FaceEntity> {
        try {
            return faceDao.getAllEnabledFaces().filter { !it.hasFaceImage() }
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取无图片的人脸数据失败", e)
        }
    }

    /**
     * 统计图片存储信息
     */
    suspend fun getImageStorageStats(): ImageStorageStats {
        try {
            val allFaces = faceDao.getAllEnabledFaces()
            val facesWithImages = allFaces.filter { it.hasFaceImage() }
            val totalImageSize = facesWithImages.sumOf { it.getFaceImageSizeKB() }

            return ImageStorageStats(
                totalFaces = allFaces.size,
                facesWithImages = facesWithImages.size,
                facesWithoutImages = allFaces.size - facesWithImages.size,
                totalImageSizeKB = totalImageSize,
                averageImageSizeKB = if (facesWithImages.isNotEmpty()) totalImageSize / facesWithImages.size else 0
            )
        } catch (e: Exception) {
            throw FaceRecognitionException.DatabaseException("获取图片存储统计失败", e)
        }
    }

    /**
     * 图片存储统计信息
     */
    data class ImageStorageStats(
        val totalFaces: Int,
        val facesWithImages: Int,
        val facesWithoutImages: Int,
        val totalImageSizeKB: Int,
        val averageImageSizeKB: Int
    )
}
