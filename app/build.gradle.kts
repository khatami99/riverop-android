import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

/**
 * Alamat server TIDAK ditulis di dalam kode.
 *
 * Dulu alamatnya dipatok di RetrofitClient, sehingga tiap pindah jaringan
 * berarti mengedit kode lalu memasang ulang aplikasi — dan berkas itu sampai
 * harus dikecualikan dari git supaya alamat laptop pribadi tidak ikut terkirim.
 *
 * Sekarang alamatnya dibaca dari local.properties, berkas yang memang sudah
 * diabaikan git dan berbeda-beda di tiap mesin. Ini padanan .env di sisi
 * Laravel: kodenya sama di semua tempat, yang berbeda cuma isian di luarnya.
 */
fun alamatServer(kunci: String): String? {
    val berkas = rootProject.file("local.properties")
    if (!berkas.exists()) return null
    val isi = Properties()
    berkas.inputStream().use { isi.load(it) }
    return isi.getProperty(kunci)?.trim()?.takeIf { it.isNotEmpty() }
}

android {
    namespace = "com.pupr.kalsel.riverop"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pupr.kalsel.riverop"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Bawaannya lewat kabel USB: "adb reverse tcp:8000 tcp:8000"
            // membuat 127.0.0.1 di HP menunjuk ke laptop. Alamat ini berlaku
            // di mesin siapa pun, jadi aman ikut masuk git — tidak ada lagi
            // IP pribadi yang tercecer di dalam kode.
            //
            // Mau lewat wifi? Tulis di local.properties:
            //   riverop.baseUrl=http://192.168.1.8:8000/
            buildConfigField(
                "String",
                "BASE_URL",
                "\"${alamatServer("riverop.baseUrl") ?: "http://127.0.0.1:8000/"}\""
            )
        }

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Alamat rilis TIDAK punya nilai cadangan yang bisa dipakai.
            //
            // Kalau build rilis diam-diam memakai alamat bawaan, suatu hari
            // APK yang menunjuk laptop pribadi akan terbagikan ke pegawai
            // dinas, dan tidak ada yang menyadarinya sampai gagal di lapangan.
            //
            // Pemeriksaannya hanya berlaku saat yang diminta memang tugas
            // rilis; kalau tidak, build debug sehari-hari ikut terhenti cuma
            // karena alamat produksi belum ada — padahal memang belum waktunya.
            val sedangRilis = gradle.startParameter.taskNames
                .any { it.contains("Release", ignoreCase = true) }

            val alamat = alamatServer("riverop.baseUrl.release")

            if (sedangRilis && alamat == null) {
                throw GradleException(
                    """
                    riverop.baseUrl.release belum diisi di local.properties.
                    Isi dengan alamat server dinas, contoh:
                      riverop.baseUrl.release=https://op-sungai.contoh.go.id/
                    """.trimIndent()
                )
            }

            if (alamat != null && !alamat.startsWith("https://")) {
                throw GradleException(
                    """
                    riverop.baseUrl.release harus memakai https://, bukan http://.
                    Tanpa itu, sandi juru dan isi laporan berjalan tanpa enkripsi.
                    """.trimIndent()
                )
            }

            // Cadangan yang sengaja tidak bisa dihubungi. TLD .invalid dijamin
            // tidak pernah ada, jadi APK rilis yang lolos tanpa alamat sungguhan
            // akan gagal menyambung — bukan diam-diam mengirim data entah ke mana.
            val alamatRilis = alamat ?: "https://belum-diatur.invalid/"

            buildConfigField("String", "BASE_URL", "\"$alamatRilis\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    //OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    //PhotoView
    implementation("io.getstream:photoview:1.0.0")

    // ExifInterface untuk baca orientasi foto
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Lokasi GPS untuk menandai titik bangunan yang diperiksa
    implementation("com.google.android.gms:play-services-location:21.3.0")
}