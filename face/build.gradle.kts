import com.android.build.gradle.internal.utils.createPublishingInfoForApp
import org.jetbrains.kotlin.load.kotlin.signatures

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.ksp)
    id("maven-publish")
}

android {
    namespace = "com.holder.face"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // 人脸检测
    api(libs.play.services.mlkit.text.recognition)
    api(libs.face.detection)

    // TensorFlow Lite
    api(libs.tensorflow.lite)

    // Room database
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)


    // Gson
    implementation(libs.gson)

    // Coroutines
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.play.services)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                group = "com.lee.face.recognition"
                artifactId = "face"
                version = "0.0.18"

                afterEvaluate {
                    artifact(tasks.getByName("bundleReleaseAar"))
                }

                // 添加POM文件生成，包含依赖信息
                pom {
                    name.set("Face Recognition Library")
                    description.set("Android face recognition library")

                    withXml {
                        val dependenciesNode = asNode().appendNode("dependencies")

                        // 手动添加API依赖
                        configurations.api.get().allDependencies.forEach { dep ->
                            if (dep.group != null && dep.name != "unspecified") {
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", dep.group)
                                dependencyNode.appendNode("artifactId", dep.name)
                                dependencyNode.appendNode("version", dep.version)
                                dependencyNode.appendNode("scope", "compile")
                            }
                        }

                        // 添加implementation依赖
                        configurations.implementation.get().allDependencies.forEach { dep ->
                            if (dep.group != null && dep.name != "unspecified") {
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", dep.group)
                                dependencyNode.appendNode("artifactId", dep.name)
                                dependencyNode.appendNode("version", dep.version)
                                dependencyNode.appendNode("scope", "runtime")
                            }
                        }
                    }
                }
            }
        }
    }
}