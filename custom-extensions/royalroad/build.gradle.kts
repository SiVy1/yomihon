plugins {
    alias(mihonx.plugins.android.application)
}

android {
    namespace = "app.yomihon.extension.en.royalroad"

    defaultConfig {
        applicationId = "app.yomihon.extension.en.royalroad"
        versionCode = 2
        versionName = "1.5.1"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    compileOnly(projects.sourceApi)
    compileOnly(projects.core.common)
}