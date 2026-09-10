package com.pupr.kalsel.riverop.utils

import android.content.Context
import com.pupr.kalsel.riverop.BuildConfig
import com.pupr.kalsel.riverop.data.remote.RetrofitClient

/**
 * Alamat server yang bisa diganti dari dalam aplikasi, hanya pada build debug.
 *
 * Dipakai ketika servernya belum punya alamat tetap — terowongan sementara,
 * atau IP laptop yang berubah tiap ganti jaringan. Tanpa ini, alamat berubah
 * berarti membangun ulang APK, dan orang yang memegang HP di lapangan belum
 * tentu bisa melakukannya.
 *
 * Build rilis sengaja mengabaikan seluruh isi berkas ini: aplikasi yang boleh
 * ditunjukkan ke server mana saja bisa diarahkan ke server palsu.
 */
object AlamatServer {

    private const val KUNCI = "alamat_server"

    fun terapkan(context: Context) {
        if (!BuildConfig.DEBUG) return
        tersimpan(context)?.let { RetrofitClient.setBaseUrl(it) }
    }

    fun tersimpan(context: Context): String? =
        PrefsHelper.get(context).getString(KUNCI, null)?.takeIf { it.isNotBlank() }

    fun simpan(context: Context, alamat: String) {
        val rapi = alamat.trim().let { if (it.endsWith("/")) it else "$it/" }
        PrefsHelper.get(context).edit().putString(KUNCI, rapi).apply()
        RetrofitClient.setBaseUrl(rapi)
    }

    fun kembalikanBawaan(context: Context) {
        PrefsHelper.get(context).edit().remove(KUNCI).apply()
        RetrofitClient.setBaseUrl(BuildConfig.BASE_URL)
    }

    fun sah(alamat: String): Boolean {
        val a = alamat.trim()
        return (a.startsWith("http://") || a.startsWith("https://")) && a.length > 10
    }
}
