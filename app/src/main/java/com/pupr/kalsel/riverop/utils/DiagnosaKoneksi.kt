package com.pupr.kalsel.riverop.utils

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pupr.kalsel.riverop.BuildConfig
import com.pupr.kalsel.riverop.R
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Menjawab "kenapa aplikasinya tidak mau jalan" dalam hitungan detik.
 *
 * Tanpa ini, gagal terhubung terlihat sama persis dengan gagal karena sebab
 * lain: aplikasi diam, dan penyebabnya harus ditebak di tempat.
 */
object DiagnosaKoneksi {

    private const val BATAS_MS = 8_000L

    private sealed interface Hasil {
        data class Sampai(val lama: Long) : Hasil
        data class SalahAlamat(val kode: Int) : Hasil
        object TidakTerjangkau : Hasil
        object Kehabisan : Hasil
    }

    fun tampilkan(activity: Activity) {
        val tampilan = activity.layoutInflater.inflate(R.layout.dialog_uji_koneksi, null)
        val tvAlamat = tampilan.findViewById<android.widget.TextView>(R.id.tvAlamatServer)
        val tvHasil = tampilan.findViewById<android.widget.TextView>(R.id.tvHasilUji)
        val tvVersi = tampilan.findViewById<android.widget.TextView>(R.id.tvVersiAplikasi)
        val barisMenunggu = tampilan.findViewById<View>(R.id.barisMenunggu)

        tvAlamat.text = RetrofitClient.getBaseUrl()
        tvVersi.text = "Versi ${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE}"

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Uji Koneksi Server")
            .setView(tampilan)
            .setPositiveButton("Uji Sekarang", null)
            .setNegativeButton("Tutup", null)
            .create()

        // Tombol dipasang sesudah dialog tampil supaya menekannya tidak
        // menutup dialog — hasilnya justru ditampilkan di dalam dialog itu.
        dialog.setOnShowListener {
            val tombol = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            tombol.setOnClickListener {
                tombol.isEnabled = false
                tvHasil.visibility = View.GONE
                barisMenunggu.visibility = View.VISIBLE

                (activity as LifecycleOwner).lifecycleScope.launch {
                    val hasil = uji()
                    barisMenunggu.visibility = View.GONE
                    tombol.isEnabled = true
                    tampilkanHasil(activity, tvHasil, hasil)
                }
            }
            tombol.performClick()
        }

        dialog.show()
    }

    private suspend fun uji(): Hasil {
        val mulai = System.currentTimeMillis()

        return withTimeoutOrNull(BATAS_MS) {
            try {
                val kode = RetrofitClient.getApiService().ujiKoneksi().code()

                // 401 justru jawaban yang benar: permintaan tadi sengaja
                // dikirim tanpa token, jadi server RiverOP yang sehat pasti
                // menolaknya. Kode lain berarti yang menjawab bukan dia.
                if (kode == 401) Hasil.Sampai(System.currentTimeMillis() - mulai)
                else Hasil.SalahAlamat(kode)
            } catch (e: UnknownHostException) {
                Hasil.TidakTerjangkau
            } catch (e: SocketTimeoutException) {
                Hasil.Kehabisan
            } catch (e: Exception) {
                Hasil.TidakTerjangkau
            }
        } ?: Hasil.Kehabisan
    }

    private fun tampilkanHasil(
        activity: Activity,
        tv: android.widget.TextView,
        hasil: Hasil
    ) {
        val (latar, teks, pesan) = when (hasil) {
            is Hasil.Sampai -> Triple(
                R.color.kondisi_normal_bg, R.color.kondisi_normal,
                "Server terjangkau dan menjawab dengan benar (${hasil.lama} ms).\n\n" +
                    "Bila masih gagal masuk, berarti masalahnya pada nama pengguna " +
                    "atau kata sandi, bukan pada jaringan."
            )

            is Hasil.TidakTerjangkau -> Triple(
                R.color.kondisi_rusak_bg, R.color.kondisi_rusak,
                "Server tidak dapat dihubungi.\n\n" +
                    "Periksa berurutan:\n" +
                    "1. Perangkat ini dan server berada pada jaringan yang sama.\n" +
                    "2. Alamat di atas sudah benar, termasuk nomor portnya.\n" +
                    "3. Sebagian jaringan kantor memisahkan antarperangkat. " +
                    "Coba lewat hotspot atau kabel USB."
            )

            is Hasil.Kehabisan -> Triple(
                R.color.warn_bg, R.color.warn_text,
                "Server tidak menjawab dalam 8 detik.\n\n" +
                    "Alamatnya kemungkinan benar, tetapi jaringannya sangat lambat " +
                    "atau ada yang menahan di tengah jalan."
            )

            is Hasil.SalahAlamat -> Triple(
                R.color.warn_bg, R.color.warn_text,
                "Ada yang menjawab di alamat itu, tetapi bukan server RiverOP " +
                    "(kode ${hasil.kode}).\n\n" +
                    "Biasanya alamatnya tertuju ke tempat lain, atau jaringan ini " +
                    "memakai halaman persetujuan yang harus dibuka lebih dahulu."
            )
        }

        tv.setBackgroundColor(ContextCompat.getColor(activity, latar))
        tv.setTextColor(ContextCompat.getColor(activity, teks))
        tv.text = pesan
        tv.visibility = View.VISIBLE
    }
}
