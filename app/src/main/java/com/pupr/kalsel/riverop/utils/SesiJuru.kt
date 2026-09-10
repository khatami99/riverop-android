package com.pupr.kalsel.riverop.utils

import android.content.Context
import androidx.work.WorkManager

/**
 * Satu-satunya tempat yang tahu cara membuang sesi juru.
 *
 * Membuang sesi bukan sekadar menghapus token: ada dua tempat penyimpanan
 * preferensi, dan ada pekerjaan sinkronisasi terjadwal yang akan terus
 * berjalan tiap lima belas menit membawa token yang sudah tidak berlaku.
 * Sebelum ini pengetahuan itu hanya hidup di dalam DashboardActivity, dan
 * satu-satunya jalan keluar dari aplikasi adalah menekan tombol Keluar.
 *
 * Kini sesi bisa gugur dari tiga arah — juru menekan Keluar, server menolak
 * token saat aplikasi dibuka, atau pekerja latar kena tolak di tengah malam —
 * dan ketiganya harus membersihkan hal yang persis sama. Kalau salah satunya
 * lupa membatalkan pekerjaan terjadwal, HP itu akan mengetuk server dengan
 * token mati sembilan puluh enam kali sehari, selamanya.
 */
object SesiJuru {

    /** Nama pekerjaan sinkronisasi, disalin dari tempat pekerjaan itu didaftarkan. */
    private const val KERJA_BERKALA = "river_op_sync"
    private const val KERJA_MANUAL = "river_op_manual_sync"

    fun bersihkan(context: Context) {
        val app = context.applicationContext

        PrefsHelper.get(app).edit().clear().apply()

        app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE)
            .edit().clear().apply()

        WorkManager.getInstance(app).apply {
            cancelUniqueWork(KERJA_BERKALA)
            cancelUniqueWork(KERJA_MANUAL)
        }
    }
}
