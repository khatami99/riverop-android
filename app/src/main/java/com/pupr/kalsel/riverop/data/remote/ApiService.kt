package com.pupr.kalsel.riverop.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<LogoutResponse>

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
}