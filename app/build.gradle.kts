plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.shahabkekhushi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.shahabkekhushi"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")


    implementation("com.google.android.gms:play-services-location:20.0.0")
    implementation("com.mapbox.navigationcore:navigation:3.5.0-beta.1")
    implementation("com.mapbox.navigationcore:ui-components:3.3.0")

//    implementation ("com.mapbox.navigation:android:2..0")

    //search
    implementation ("com.mapbox.search:discover:2.5.0")
    implementation ("com.mapbox.search:place-autocomplete:2.5.0")
    implementation ("com.mapbox.search:mapbox-search-android:2.5.0")
    implementation ("com.mapbox.search:offline:2.5.0")
    implementation ("com.mapbox.search:mapbox-search-android-ui:2.5.0")

    implementation ("com.airbnb.android:lottie:5.0.3")










}