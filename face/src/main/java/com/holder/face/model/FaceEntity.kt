package com.holder.face.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 人脸数据库实体
 * Room数据库表结构定义
 */
@Entity(
    tableName = "face_vectors",
    indices = [
        Index(value = ["person_id"], unique = true),
        Index(value = ["created_time"])
    ]
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** 人员ID (后端返回的ID，不做自增维护) */
    @ColumnInfo(name = "person_id")
    val personId: String,

    /** 特征向量 (存储为字节数组) */
    @ColumnInfo(name = "vector_data", typeAffinity = ColumnInfo.BLOB)
    val vectorData: ByteArray,

    /** 向量维度 */
    @ColumnInfo(name = "vector_dimension")
    val vectorDimension: Int,

    /** 存储时间 */
    @ColumnInfo(name = "created_time")
    val createdTime: Date,

    /** 最后更新时间 */
    @ColumnInfo(name = "updated_time")
    val updatedTime: Date = createdTime,

    /** 置信度 (可选) */
    @ColumnInfo(name = "confidence")
    val confidence: Float? = null,

    /** 备注信息 */
    @ColumnInfo(name = "remarks")
    val remarks: String? = null,

    /** 是否启用 */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    /** 版本号 (用于数据同步) */
    @ColumnInfo(name = "version")
    val version: Int = 1,

    /** 人脸图片Base64编码 (可选) */
    @ColumnInfo(name = "face_image_base64")
    val faceImageBase64: String? = null
) {

    companion object {
        /**
         * 从FaceVector创建FaceEntity
         */
        fun fromFaceVector(
            faceVector: FaceVector,
            remarks: String? = null,
            isEnabled: Boolean = true,
            faceImageBase64: String? = null
        ): FaceEntity {
            return FaceEntity(
                personId = faceVector.personId,
                vectorData = faceVector.toByteArray(),
                vectorDimension = faceVector.dimension,
                createdTime = faceVector.createdTime,
                updatedTime = Date(),
                confidence = faceVector.confidence,
                remarks = remarks,
                isEnabled = isEnabled,
                faceImageBase64 = faceImageBase64
            )
        }
    }

    /**
     * 转换为FaceVector
     */
    fun toFaceVector(): FaceVector {
        return FaceVector.fromByteArray(
            personId = personId,
            byteArray = vectorData,
            createdTime = createdTime
        ).copy(confidence = confidence, originFaceEntity = this)
    }

    /**
     * 更新向量数据
     */
    fun updateVector(newVector: FaceVector): FaceEntity {
        return copy(
            vectorData = newVector.toByteArray(),
            vectorDimension = newVector.dimension,
            updatedTime = Date(),
            confidence = newVector.confidence,
            version = version + 1
        )
    }

    /**
     * 启用/禁用
     */
    fun setEnabled(enabled: Boolean): FaceEntity {
        return copy(
            isEnabled = enabled,
            updatedTime = Date(),
            version = version + 1
        )
    }

    /**
     * 更新备注
     */
    fun updateRemarks(newRemarks: String?): FaceEntity {
        return copy(
            remarks = newRemarks,
            updatedTime = Date(),
            version = version + 1
        )
    }

    /**
     * 更新人脸图片
     */
    fun updateFaceImage(newImageBase64: String?): FaceEntity {
        return copy(
            faceImageBase64 = newImageBase64,
            updatedTime = Date(),
            version = version + 1
        )
    }

    /**
     * 检查数据是否有效
     */
    fun isValid(): Boolean {
        return personId.isNotBlank() &&
                vectorData.isNotEmpty() &&
                vectorDimension > 0 &&
                vectorData.size == vectorDimension * 4 // 每个float占4字节
    }

    /**
     * 获取存储大小 (字节)
     */
    fun getStorageSize(): Int {
        return vectorData.size +
                personId.toByteArray().size +
                (remarks?.toByteArray()?.size ?: 0) +
                (faceImageBase64?.toByteArray()?.size ?: 0) +
                64 // 其他字段的大概大小
    }

    /**
     * 检查是否有人脸图片
     */
    fun hasFaceImage(): Boolean {
        return !faceImageBase64.isNullOrBlank()
    }

    /**
     * 获取人脸图片大小估算 (KB)
     */
    fun getFaceImageSizeKB(): Int {
        return if (hasFaceImage()) {
            (faceImageBase64!!.length * 3 / 4) / 1024 // Base64解码后的大概大小
        } else {
            0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEntity

        if (id != other.id) return false
        if (personId != other.personId) return false
        if (!vectorData.contentEquals(other.vectorData)) return false
        if (vectorDimension != other.vectorDimension) return false
        if (createdTime != other.createdTime) return false
        if (updatedTime != other.updatedTime) return false
        if (confidence != other.confidence) return false
        if (remarks != other.remarks) return false
        if (isEnabled != other.isEnabled) return false
        if (version != other.version) return false
        if (faceImageBase64 != other.faceImageBase64) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + personId.hashCode()
        result = 31 * result + vectorData.contentHashCode()
        result = 31 * result + vectorDimension
        result = 31 * result + createdTime.hashCode()
        result = 31 * result + updatedTime.hashCode()
        result = 31 * result + (confidence?.hashCode() ?: 0)
        result = 31 * result + (remarks?.hashCode() ?: 0)
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + version
        result = 31 * result + (faceImageBase64?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FaceEntity(id=$id, personId='$personId', vectorDimension=$vectorDimension, " +
                "createdTime=$createdTime, updatedTime=$updatedTime, confidence=$confidence, " +
                "remarks=$remarks, isEnabled=$isEnabled, version=$version, " +
                "hasFaceImage=${hasFaceImage()}, imageSize=${getFaceImageSizeKB()}KB)"
    }
}
