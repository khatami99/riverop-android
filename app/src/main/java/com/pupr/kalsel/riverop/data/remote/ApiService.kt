package com.pupr.kalsel.riverop.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<LogoutResponse>

    // Sengaja tanpa Authorization: jawaban 401 sudah membuktikan server
    // terjangkau DAN memang server RiverOP, tanpa perlu sesi yang sah.
    @GET("api/master/sync")
    suspend fun ujiKoneksi(): Response<Unit>

    @GET("api/master/sync")
    suspend fun syncMaster(
        @Header("Authorization") token: String
    ): Response<MasterSyncResponse>

    @GET("api/master/profil")
    suspend fun getProfil(
        @Header("Authorization") token: String
    ): Response<ProfilResponse>

    @POST("api/laporan/sync")
    suspend fun syncLaporan(
        @Header("Authorization") token: String,
        @Body request: LaporanSyncRequest
    ): Response<LaporanSyncResponse>

    @Multipart
    @POST("api/laporan/upload-foto")
    suspend fun uploadFoto(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<UploadFotoResponse>

    @POST("api/sinkronisasi/status")
    suspend fun laporStatusSinkronisasi(
        @Header("Authorization") token: String,
        @Body request: StatusSinkronisasiRequest
    ): Response<StatusSinkronisasiResponse>

}

data class UploadFotoResponse(
    val status: String,
    val url: String
)