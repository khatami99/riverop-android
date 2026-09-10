package com.pupr.kalsel.riverop

import android.app.Application
import com.pupr.kalsel.riverop.utils.AlamatServer

/**
 * Alamat server dipasang di sini supaya sudah berlaku sebelum layar mana pun
 * sempat memanggil server — termasuk pekerja latar yang berjalan tanpa layar.
 */
class RiverOpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlamatServer.terapkan(this)
    }
}
