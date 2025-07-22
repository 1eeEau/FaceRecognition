package com.holder.face.database

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room数据库类型转换器
 * 用于处理自定义数据类型的存储和读取
 */
class Converters {
    
    /**
     * Date转换为Long (时间戳)
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    /**
     * Long (时间戳) 转换为Date
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}
