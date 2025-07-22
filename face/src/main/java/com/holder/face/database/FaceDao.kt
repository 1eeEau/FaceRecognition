package com.holder.face.database

import androidx.room.*
import com.holder.face.model.FaceEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 人脸数据访问对象
 * 定义数据库操作接口
 */
@Dao
interface FaceDao {
    
    /**
     * 插入人脸数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceEntity): Long
    
    /**
     * 批量插入人脸数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaces(faces: List<FaceEntity>): List<Long>
    
    /**
     * 更新人脸数据
     */
    @Update
    suspend fun updateFace(face: FaceEntity): Int
    
    /**
     * 删除人脸数据
     */
    @Delete
    suspend fun deleteFace(face: FaceEntity): Int
    
    /**
     * 根据人员ID删除人脸数据
     */
    @Query("DELETE FROM face_vectors WHERE person_id = :personId")
    suspend fun deleteFaceByPersonId(personId: String): Int
    
    /**
     * 删除所有人脸数据
     */
    @Query("DELETE FROM face_vectors")
    suspend fun deleteAllFaces(): Int
    
    /**
     * 根据人员ID查询人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE person_id = :personId AND is_enabled = 1")
    suspend fun getFaceByPersonId(personId: String): FaceEntity?
    
    /**
     * 根据ID查询人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE id = :id")
    suspend fun getFaceById(id: Long): FaceEntity?
    
    /**
     * 获取所有启用的人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE is_enabled = 1 ORDER BY created_time DESC")
    suspend fun getAllEnabledFaces(): List<FaceEntity>
    
    /**
     * 获取所有人脸数据 (包括禁用的)
     */
    @Query("SELECT * FROM face_vectors ORDER BY created_time DESC")
    suspend fun getAllFaces(): List<FaceEntity>
    
    /**
     * 获取启用的人脸数据流 (用于实时更新)
     */
    @Query("SELECT * FROM face_vectors WHERE is_enabled = 1 ORDER BY created_time DESC")
    fun getAllEnabledFacesFlow(): Flow<List<FaceEntity>>
    
    /**
     * 获取人脸数据总数
     */
    @Query("SELECT COUNT(*) FROM face_vectors WHERE is_enabled = 1")
    suspend fun getEnabledFaceCount(): Int
    
    /**
     * 获取所有人脸数据总数 (包括禁用的)
     */
    @Query("SELECT COUNT(*) FROM face_vectors")
    suspend fun getTotalFaceCount(): Int
    
    /**
     * 检查人员ID是否存在
     */
    @Query("SELECT EXISTS(SELECT 1 FROM face_vectors WHERE person_id = :personId AND is_enabled = 1)")
    suspend fun isPersonIdExists(personId: String): Boolean
    
    /**
     * 启用/禁用人脸数据
     */
    @Query("UPDATE face_vectors SET is_enabled = :enabled, updated_time = :updatedTime, version = version + 1 WHERE person_id = :personId")
    suspend fun setFaceEnabled(personId: String, enabled: Boolean, updatedTime: Date = Date()): Int
    
    /**
     * 更新人脸备注
     */
    @Query("UPDATE face_vectors SET remarks = :remarks, updated_time = :updatedTime, version = version + 1 WHERE person_id = :personId")
    suspend fun updateFaceRemarks(personId: String, remarks: String?, updatedTime: Date = Date()): Int
    
    /**
     * 根据创建时间范围查询人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE created_time BETWEEN :startTime AND :endTime AND is_enabled = 1 ORDER BY created_time DESC")
    suspend fun getFacesByTimeRange(startTime: Date, endTime: Date): List<FaceEntity>
    
    /**
     * 获取最近创建的N个人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE is_enabled = 1 ORDER BY created_time DESC LIMIT :limit")
    suspend fun getRecentFaces(limit: Int): List<FaceEntity>
    
    /**
     * 获取最旧的N个人脸数据 (用于清理)
     */
    @Query("SELECT * FROM face_vectors WHERE is_enabled = 1 ORDER BY created_time ASC LIMIT :limit")
    suspend fun getOldestFaces(limit: Int): List<FaceEntity>
    
    /**
     * 根据置信度范围查询人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE confidence BETWEEN :minConfidence AND :maxConfidence AND is_enabled = 1 ORDER BY confidence DESC")
    suspend fun getFacesByConfidenceRange(minConfidence: Float, maxConfidence: Float): List<FaceEntity>
    
    /**
     * 获取高置信度的人脸数据
     */
    @Query("SELECT * FROM face_vectors WHERE confidence >= :minConfidence AND is_enabled = 1 ORDER BY confidence DESC")
    suspend fun getHighConfidenceFaces(minConfidence: Float): List<FaceEntity>
    
    /**
     * 搜索人脸数据 (根据人员ID或备注)
     */
    @Query("SELECT * FROM face_vectors WHERE (person_id LIKE '%' || :keyword || '%' OR remarks LIKE '%' || :keyword || '%') AND is_enabled = 1 ORDER BY created_time DESC")
    suspend fun searchFaces(keyword: String): List<FaceEntity>
    
    /**
     * 获取数据库统计信息
     */
    @Query("""
        SELECT 
            COUNT(*) as total_count,
            COUNT(CASE WHEN is_enabled = 1 THEN 1 END) as enabled_count,
            AVG(confidence) as avg_confidence,
            MIN(created_time) as earliest_time,
            MAX(created_time) as latest_time
        FROM face_vectors
    """)
    suspend fun getDatabaseStats(): DatabaseStats
    
    /**
     * 清理过期数据 (删除指定时间之前的数据)
     */
    @Query("DELETE FROM face_vectors WHERE created_time < :beforeTime")
    suspend fun cleanupOldData(beforeTime: Date): Int
    
    /**
     * 获取向量维度统计
     */
    @Query("SELECT DISTINCT vector_dimension FROM face_vectors")
    suspend fun getVectorDimensions(): List<Int>
    
    /**
     * 根据版本号查询更新的数据 (用于数据同步)
     */
    @Query("SELECT * FROM face_vectors WHERE version > :lastVersion ORDER BY version ASC")
    suspend fun getUpdatedFaces(lastVersion: Int): List<FaceEntity>
}

/**
 * 数据库统计信息数据类
 */
data class DatabaseStats(
    val total_count: Int,
    val enabled_count: Int,
    val avg_confidence: Float?,
    val earliest_time: Date?,
    val latest_time: Date?
)
