# 人脸识别库 (Face Recognition Library)

一个基于Android平台的高性能人脸识别库，提供完整的人脸检测、特征提取、存储和识别功能。

## 特性

- ✅ **高精度识别**: 基于Google MLKit人脸检测 + TensorFlow Lite特征提取
- ✅ **本地存储**: 使用Room数据库进行本地数据管理
- ✅ **可配置参数**: 支持识别阈值、存储容量等多项配置
- ✅ **健壮性强**: 完整的异常处理和错误恢复机制
- ✅ **异步支持**: 基于Kotlin协程的异步操作
- ✅ **内存优化**: 智能的资源管理和内存回收
- ✅ **多种算法**: 支持余弦相似度、欧几里得距离等多种比较算法

## 架构设计

```
人脸识别库架构
├── 配置管理层 (config/)
│   └── FaceRecognitionConfig - 参数配置管理
├── 数据模型层 (model/)
│   ├── FaceVector - 人脸向量数据模型
│   ├── RecognitionResult - 识别结果模型
│   └── FaceEntity - 数据库实体
├── 数据存储层 (database/)
│   ├── FaceDatabase - Room数据库
│   ├── FaceDao - 数据访问对象
│   └── FaceRepository - 数据仓库
├── 核心处理层 (core/)
│   ├── FaceDetector - 人脸检测
│   ├── FeatureExtractor - 特征提取
│   └── FaceComparator - 人脸比较
├── 工具层 (utils/)
│   ├── VectorUtils - 向量计算工具
│   └── ImageUtils - 图像处理工具
├── 管理层 (manager/)
│   └── FaceRecognitionManager - 主要API接口
└── 异常处理 (exception/)
    └── FaceRecognitionException - 自定义异常
```

## 快速开始

### 1. 依赖配置

在 `app/build.gradle.kts` 中添加必要的依赖：

```kotlin
dependencies {
    // 人脸检测
    implementation("com.google.mlkit:face-detection:16.1.5")
    
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    
    // Room数据库
    implementation("androidx.room:room-runtime:2.5.0")
    implementation("androidx.room:room-ktx:2.5.0")
    kapt("androidx.room:room-compiler:2.5.0")
    
    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // 其他依赖...
}
```

### 2. 模型文件

将 `MobileFaceNet.tflite` 模型文件放置在 `app/src/main/assets/` 目录下。

### 3. 基本使用

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var faceRecognitionManager: FaceRecognitionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            initializeFaceRecognition()
        }
    }
    
    private suspend fun initializeFaceRecognition() {
        try {
            // 1. 创建配置
            val config = FaceRecognitionConfig.builder()
                .maxFaceCount(50) // 最大存储50个人脸
                .recognitionThreshold(0.8f) // 识别阈值
                .enableDebugLog(true) // 启用调试日志
                .build()
            
            // 2. 获取管理器实例
            faceRecognitionManager = FaceRecognitionManager.getInstance(this@MainActivity, config)
            
            // 3. 初始化系统
            faceRecognitionManager.initialize()
            
            Log.i("FaceRecognition", "初始化完成")
            
        } catch (e: Exception) {
            Log.e("FaceRecognition", "初始化失败", e)
        }
    }
}
```

## 主要功能

### 人脸注册

```kotlin
suspend fun registerFace(bitmap: Bitmap, personId: String) {
    val result = faceRecognitionManager.registerFace(bitmap, personId, "备注信息")
    
    if (result.isSuccess) {
        Log.i("FaceRecognition", "注册成功: ${result.personId}")
    } else {
        Log.w("FaceRecognition", "注册失败: ${result.errorMessage}")
    }
}
```

### 人脸识别

```kotlin
suspend fun recognizeFace(bitmap: Bitmap) {
    val result = faceRecognitionManager.recognizeFace(bitmap)
    
    when {
        result.isSuccess -> {
            Log.i("FaceRecognition", "识别成功: ${result.personId}, 置信度: ${result.confidence}")
        }
        result.detectedFaceCount == 0 -> {
            Log.w("FaceRecognition", "未检测到人脸")
        }
        result.detectedFaceCount > 1 -> {
            Log.w("FaceRecognition", "检测到多个人脸")
        }
        else -> {
            Log.w("FaceRecognition", "识别失败: ${result.errorMessage}")
        }
    }
}
```

### 数据管理

```kotlin
// 获取所有人脸数据
val allFaces = faceRecognitionManager.getAllFaces()

// 获取特定人脸
val face = faceRecognitionManager.getFace("person_001")

// 删除人脸
val deleted = faceRecognitionManager.deleteFace("person_001")

// 获取人脸数量
val count = faceRecognitionManager.getFaceCount()

// 获取剩余容量
val remaining = faceRecognitionManager.getRemainingCapacity()

// 清空所有数据 (谨慎使用)
val cleared = faceRecognitionManager.clearAllFaces()
```

## 配置选项

```kotlin
val config = FaceRecognitionConfig.builder()
    .maxFaceCount(100) // 最大人脸数量 (默认50)
    .recognitionThreshold(0.85f) // 识别阈值 (默认0.8)
    .featureVectorDimension(512) // 特征向量维度 (默认512)
    .minFaceSize(80) // 最小人脸尺寸 (默认50)
    .maxFaceSize(800) // 最大人脸尺寸 (默认1000)
    .faceDetectionConfidence(0.8f) // 人脸检测置信度 (默认0.7)
    .enableDebugLog(true) // 启用调试日志 (默认false)
    .databaseName("my_face_db.db") // 数据库名称
    .modelFileName("MobileFaceNet.tflite") // 模型文件名
    .similarityMethod(FaceRecognitionConfig.SimilarityMethod.COSINE) // 相似度算法
    .build()
```

## 数据库结构

人脸数据存储在SQLite数据库中，表结构如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 主键 (自增) |
| person_id | String | 人员ID (后端返回的ID) |
| vector_data | ByteArray | 特征向量数据 |
| vector_dimension | Int | 向量维度 |
| created_time | Date | 创建时间 |
| updated_time | Date | 更新时间 |
| confidence | Float | 置信度 |
| remarks | String | 备注信息 |
| is_enabled | Boolean | 是否启用 |
| version | Int | 版本号 |

## 异常处理

库提供了完整的异常处理机制：

```kotlin
try {
    val result = faceRecognitionManager.registerFace(bitmap, personId)
    // 处理结果...
} catch (e: FaceRecognitionException.StorageFullException) {
    // 存储空间已满
} catch (e: FaceRecognitionException.FaceDetectionException) {
    // 人脸检测失败
} catch (e: FaceRecognitionException.FeatureExtractionException) {
    // 特征提取失败
} catch (e: FaceRecognitionException.DatabaseException) {
    // 数据库操作失败
} catch (e: FaceRecognitionException) {
    // 其他人脸识别相关异常
}
```

## 性能优化建议

1. **图像预处理**: 确保输入图像质量良好，避免模糊、过暗或过亮的图像
2. **人脸尺寸**: 保持人脸在合适的尺寸范围内 (建议80-400像素)
3. **存储管理**: 定期清理不需要的人脸数据，避免存储空间不足
4. **异步操作**: 所有识别操作都应在后台线程进行，避免阻塞UI
5. **资源释放**: 在适当的时机调用 `release()` 方法释放资源

## 注意事项

1. **模型文件**: 确保 `MobileFaceNet.tflite` 文件正确放置在assets目录
2. **权限**: 如果需要访问相机或存储，请确保已获得相应权限
3. **线程安全**: 管理器实例是线程安全的，可以在多个协程中使用
4. **内存管理**: 及时回收不需要的Bitmap对象，避免内存泄漏
5. **数据备份**: 重要的人脸数据建议进行备份

## 示例代码

完整的使用示例请参考：
- `MainActivity.kt` - 基本集成示例
- `FaceRecognitionExample.kt` - 详细功能演示

## 技术支持

如有问题或建议，请查看代码注释或联系开发团队。
