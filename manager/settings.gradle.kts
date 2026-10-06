@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// 注意：不要在这里加国内镜像。镜像若代理了某个库的 POM 却没有对应的
// AAR，Gradle 会认定该仓库为唯一来源并直接失败（CI 上 libsu 就是这么挂的）。
// 国内网络加速请放到 ~/.gradle/init.d/ 里，只影响本地，不进仓库。
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "KernelSU"
include(":app")
