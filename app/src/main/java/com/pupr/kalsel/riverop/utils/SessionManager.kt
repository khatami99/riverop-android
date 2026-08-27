package com.pupr.kalsel.riverop.utils

import android.os.Handler
import android.os.Looper

object SessionManager {
    var onUnauthorized: (() -> Unit)? = null

    fun notifyUnauthorized() {
        Handler(Looper.getMainLooper()).post {
            onUnauthorized?.invoke()
        }
    }
}
