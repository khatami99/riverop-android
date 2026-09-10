package com.pupr.kalsel.riverop.models

/**
 * Keadaan sebuah laporan di perangkat juru.
 *
 * Labelnya dibaca langsung oleh juru pada daftar laporan, jadi ditulis dalam
 * bahasa Indonesia yang baku dan menerangkan diri sendiri. "Gagal Sync" dulu
 * berdiri di sini — separuh Indonesia separuh Inggris, dan justru pada baris
 * yang paling perlu dimengerti ketika ada yang tidak beres.
 */
enum class SyncStatus(val label: String) {
    DRAFT("Konsep"),
    LOCAL("Siap Dikirim"),
    SYNCING("Sedang Dikirim"),
    SYNCED("Terkirim"),
    FAILED("Gagal Terkirim")
}
