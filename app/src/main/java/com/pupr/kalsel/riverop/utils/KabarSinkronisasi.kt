package com.pupr.kalsel.riverop.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pupr.kalsel.riverop.R
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity

/**
 * Kabar kepada juru mengenai nasib laporannya.
 *
 * Sinkronisasi berjalan di latar lewat WorkManager, dan sebelum ini ia
 * sepenuhnya bisu. Juru yang menutup aplikasi sesudah mengisi laporan tidak
 * punya cara tahu apakah laporannya sudah sampai ke dinas atau masih
 * tertahan di HP-nya — dan yang kedua itu bukan hal sepele: laporan yang
 * tidak sampai berarti pekerjaan lapangan yang dianggap tidak pernah terjadi.
 *
 * Dua aturan yang menentukan bentuknya:
 *
 * PERTAMA, tidak berkabar bila tidak ada yang dikerjakan. Pekerja ini
 * berjalan tiap lima belas menit; memberi tahu "tidak ada yang perlu
 * dikirim" sebanyak sembilan puluh enam kali sehari akan membuat pemakainya
 * mematikan seluruh notifikasi aplikasi ini — termasuk yang benar-benar
 * penting.
 *
 * KEDUA, kabar gagal tidak berbunyi seperti kesalahan pemakainya. Penyebab
 * yang paling lazim di lapangan adalah tidak ada sinyal, dan itu bukan
 * sesuatu yang bisa ia perbaiki. Karena itu kalimatnya menyebutkan bahwa
 * percobaan akan diulang sendiri.
 */
object KabarSinkronisasi {

    private const val SALURAN = "sinkronisasi"

    // Id tetap dan berbeda per jenis: kabar sejenis saling menimpa alih-alih
    // menumpuk, tetapi kabar berhasil tidak menghapus kabar gagal.
    private const val ID_BERHASIL = 1001
    private const val ID_GAGAL = 1002

    fun siapkanSaluran(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val saluran = NotificationChannel(
            SALURAN,
            "Pengiriman laporan",
            // Bukan HIGH: ini kabar rutin, bukan sesuatu yang harus memotong
            // apa pun yang sedang dikerjakan juru di tepi sungai.
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Kabar saat laporan berhasil atau gagal terkirim ke dinas."
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(saluran)
    }

    fun berhasil(context: Context, jumlah: Int) {
        if (jumlah <= 0) return

        tampilkan(
            context,
            ID_BERHASIL,
            "Laporan terkirim",
            "$jumlah laporan sudah sampai ke dinas."
        )
    }

    fun gagal(context: Context, jumlah: Int) {
        if (jumlah <= 0) return

        tampilkan(
            context,
            ID_GAGAL,
            "Laporan belum terkirim",
            "$jumlah laporan masih tertahan di HP ini. Akan dicoba lagi sendiri " +
                "begitu ada jaringan — datanya tidak hilang."
        )
    }

    /**
     * Menarik kembali kabar gagal yang sudah tidak berlaku.
     *
     * Tanpa ini, kabar "belum terkirim" tetap menggantung di panel notifikasi
     * sesudah laporannya benar-benar sampai — dan juru yang membukanya jam
     * berikutnya akan mengira pekerjaannya masih tertahan, lalu menyinkronkan
     * lagi dan lagi. Kabar yang salah lebih buruk daripada tidak berkabar.
     */
    fun bersihkanGagal(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_GAGAL)
    }

    private fun tampilkan(context: Context, id: Int, judul: String, isi: String) {
        // Sejak Android 13 notifikasi perlu izin, dan menembakkannya tanpa izin
        // melempar SecurityException. Kegagalan berkabar tidak boleh
        // menjatuhkan sinkronisasi yang justru berhasil.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        siapkanSaluran(context)

        val buka = PendingIntent.getActivity(
            context,
            id,
            Intent(context, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val kabar = NotificationCompat.Builder(context, SALURAN)
            .setSmallIcon(R.drawable.ic_stat_sinkronisasi)
            .setContentTitle(judul)
            .setContentText(isi)
            // Kalimatnya panjang dan justru bagian akhirnya yang menenangkan,
            // jadi harus bisa dibaca utuh saat notifikasinya dibentangkan.
            .setStyle(NotificationCompat.BigTextStyle().bigText(isi))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buka)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, kabar)
        } catch (e: SecurityException) {
            android.util.Log.w("KabarSinkronisasi", "Izin notifikasi tidak ada: ${e.message}")
        }
    }
}
