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
import com.pupr.kalsel.riverop.ui.login.LoginActivity

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

    /**
     * Sesi yang berakhir diberi salurannya sendiri, dan ini bukan kerapian.
     *
     * Aturan pertama di atas membuat kabar sinkronisasi sengaja dibuat tenang
     * dan mudah diabaikan. Tetapi kabar sesi berakhir justru menuntut juru
     * berbuat sesuatu: selama ia tidak masuk ulang, tidak ada satu pun
     * laporannya yang bisa terkirim. Bila keduanya berbagi saluran, juru yang
     * membisukan kabar rutin ikut membisukan satu-satunya kabar yang benar-
     * benar harus ia baca — dan ia baru sadar berminggu-minggu kemudian.
     */
    private const val SALURAN_SESI = "sesi"

    // Id tetap dan berbeda per jenis: kabar sejenis saling menimpa alih-alih
    // menumpuk, tetapi kabar berhasil tidak menghapus kabar gagal.
    private const val ID_BERHASIL = 1001
    private const val ID_GAGAL = 1002
    private const val ID_SESI = 1003

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

    private fun siapkanSaluranSesi(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val saluran = NotificationChannel(
            SALURAN_SESI,
            "Sesi masuk",
            // HIGH, berbeda dengan saluran sinkronisasi: selama juru belum
            // masuk ulang, seluruh pekerjaannya tertahan di HP. Ini satu-satunya
            // kabar dari aplikasi ini yang pantas memotong apa pun.
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Kabar saat juru harus masuk ulang ke aplikasi."
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
            "$jumlah laporan telah sampai ke dinas."
        )
    }

    fun gagal(context: Context, jumlah: Int) {
        if (jumlah <= 0) return

        tampilkan(
            context,
            ID_GAGAL,
            "Laporan belum terkirim",
            "$jumlah laporan masih tertahan pada perangkat ini. Pengiriman akan " +
                "diulang secara otomatis begitu jaringan tersedia. Data tidak hilang."
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

    /**
     * Mengabarkan bahwa server menolak sesi juru.
     *
     * Sebelum ada kabar ini, penolakan itu sepenuhnya bisu: pekerja latar
     * berhenti, laporan tetap bertumpuk di HP, dan juru terus mencatat tanpa
     * tahu bahwa tidak ada satu pun yang sampai ke dinas. Yang lebih dulu
     * mengetahuinya justru dinas, lewat halaman Sinkronisasi — juru baru sadar
     * ketika ditelepon.
     *
     * Kabar gagal biasa ikut ditarik, sebab ia menyuruh juru menunggu jaringan,
     * dan menunggu tidak akan menyelesaikan apa pun di sini.
     */
    fun sesiBerakhir(context: Context) {
        bersihkanGagal(context)

        tampilkan(
            context,
            ID_SESI,
            "Perlu masuk kembali",
            "Sesi pada perangkat ini sudah tidak berlaku, sehingga laporan tidak " +
                "dapat dikirim ke dinas. Ketuk untuk masuk kembali. Laporan yang " +
                "sudah dicatat tetap tersimpan dan akan terkirim sesudahnya.",
            SALURAN_SESI,
            Intent(context, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    private fun tampilkan(
        context: Context,
        id: Int,
        judul: String,
        isi: String,
        saluran: String = SALURAN,
        tujuan: Intent = Intent(context, DashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    ) {
        // Sejak Android 13 notifikasi perlu izin, dan menembakkannya tanpa izin
        // melempar SecurityException. Kegagalan berkabar tidak boleh
        // menjatuhkan sinkronisasi yang justru berhasil.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (saluran == SALURAN_SESI) siapkanSaluranSesi(context) else siapkanSaluran(context)

        val buka = PendingIntent.getActivity(
            context,
            id,
            tujuan,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val kabar = NotificationCompat.Builder(context, saluran)
            .setSmallIcon(R.drawable.ic_stat_sinkronisasi)
            .setContentTitle(judul)
            .setContentText(isi)
            // Kalimatnya panjang dan justru bagian akhirnya yang menenangkan,
            // jadi harus bisa dibaca utuh saat notifikasinya dibentangkan.
            .setStyle(NotificationCompat.BigTextStyle().bigText(isi))
            .setPriority(
                if (saluran == SALURAN_SESI) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
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
