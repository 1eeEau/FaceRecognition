package com.holder.face.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.holder.face.model.FaceEntity

/**
 * 人脸识别数据库
 * Room数据库主类
 */
@Database(
    entities = [FaceEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {
    
    /**
     * 获取人脸数据访问对象
     */
    abstract fun faceDao(): FaceDao
    
    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null
        
        /**
         * 获取数据库实例 (单例模式)
         */
        fun getDatabase(context: Context, databaseName: String = "face_recognition.db"): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    databaseName
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2) // 预留迁移
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 清除数据库实例 (用于测试)
         */
        fun clearInstance() {
            INSTANCE = null
        }
        
        /**
         * 数据库回调
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 数据库创建时的初始化操作
                // 可以在这里插入默认数据或执行初始化脚本
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // 数据库打开时的操作
                // 可以在这里执行一些维护操作
            }
        }
        
        /**
         * 数据库迁移 (示例，从版本1到版本2)
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 示例迁移：添加新字段
                // database.execSQL("ALTER TABLE face_vectors ADD COLUMN new_field TEXT")
            }
        }
    }
}
