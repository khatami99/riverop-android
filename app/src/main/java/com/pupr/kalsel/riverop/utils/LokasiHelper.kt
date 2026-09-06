package com.pupr.kalsel.riverop.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mengambil titik lokasi untuk menandai bangunan yang sedang diperiksa.
 *
 * Juru sungai bekerja di lapangan dengan sinyal yang sering buruk, sehingga
 * pengambilan lokasi dibuat tidak pernah menggantung: selalu ada batas waktu
 * dan selalu mengembalikan hasil, termasuk saat gagal.
 */
class LokasiHelper(private val context: Context) {

    sealed interface Hasil {
        data class Berhasil(val lat: Double, val lng: Double, val akurasiMeter: Float) : Hasil
        data object IzinDitolak : Hasil
        data object GpsMati : Hasil
        data object TidakDitemukan : Hasil
    }

    companion object {
        val IZIN = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /** Batas tunggu; lebih lama dari ini juru lebih baik diberi tahu daripada menunggu. */
        private const val BATAS_TUNGGU_MS = 20_000L
    }

    fun adaIzin(): Boolean = IZIN.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun gpsAktif(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun ambil(): Hasil {
        if (!adaIzin()) return Hasil.IzinDitolak
        if (!gpsAktif()) return Hasil.GpsMati

        val klien = LocationServices.getFusedLocationProviderClient(context)

        val permintaan = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(BATAS_TUNGGU_MS)
            .setMaxUpdateAgeMillis(30_000L)
            .build()

        val hasil = CompletableDeferred<Hasil>()

        try {
            klien.getCurrentLocation(permintaan, null)
                .addOnSuccessListener { lokasi ->
                    hasil.complete(
                        if (lokasi != null) {
                            Hasil.Berhasil(lokasi.latitude, lokasi.longitude, lokasi.accuracy)
                        } else {
                            Hasil.TidakDitemukan
                        }
                    )
                }
                .addOnFailureListener { hasil.complete(Hasil.TidakDitemukan) }
        } catch (e: SecurityException) {
            return Hasil.IzinDitolak
        }

        // Lapisan pengaman: bila layanan lokasi tidak pernah memanggil balik,
        // fungsi ini tetap selesai dan tidak menggantung layar juru.
        return withTimeoutOrNull(BATAS_TUNGGU_MS + 5_000L) { hasil.await() } ?: Hasil.TidakDitemukan
    }
}
