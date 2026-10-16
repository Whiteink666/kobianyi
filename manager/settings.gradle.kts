@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugin/")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // 腾讯镜像加速。坑：镜像代理了 libsu 的 POM 却没有对应的 AAR，
        // Gradle 一旦在某仓库拿到元数据就只认这一处，找不到 AAR 直接失败
        // （CI 上就是这么挂的）。所以把 libsu 所在的 group 排除掉，让它走 jitpack。
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") {
            content {
                excludeGroup("com.github.topjohnwu")
            }
        }
        maven("https://jitpack.io")
        google()
        mavenCentral()
    }
}

rootProject.name = "KernelSU"
include(":app")
