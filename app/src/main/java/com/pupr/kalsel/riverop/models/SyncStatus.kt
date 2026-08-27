package com.pupr.kalsel.riverop.models

enum class SyncStatus(val label: String) {
    DRAFT("Draft"),
    LOCAL("Siap Dikirim"),
    SYNCING("Menyinkronkan..."),
    SYNCED("Tersinkronisasi"),
    FAILED("Gagal Sync")
}