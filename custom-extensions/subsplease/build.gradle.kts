plugins {
    alias(mihonx.plugins.android.application)
}

android {
    namespace = "app.yomihon.extension.en.subsplease"

    defaultConfig {
        applicationId = "app.yomihon.extension.en.subsplease"
        versionCode = 1
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
