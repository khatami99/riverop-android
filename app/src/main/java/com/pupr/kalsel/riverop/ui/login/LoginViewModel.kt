package com.pupr.kalsel.riverop.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pupr.kalsel.riverop.data.remote.LoginRequest
import com.pupr.kalsel.riverop.data.remote.LoginResponse
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import com.pupr.kalsel.riverop.utils.PrefsHelper
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(username: String, password: String) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.getApiService().login(LoginRequest(username, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        saveSession(body)
                        syncMasterData(body.token, body.user.wilayah_sungai_id)
                        _loginState.postValue(LoginState.Success(body))
                    } else {
                        _loginState.postValue(LoginState.Error("Respons kosong dari server"))
                    }
                } else {
                    val errorMsg = try {
                        val errorBody = response.errorBody()?.string()
                        val json = JSONObject(errorBody ?: "")
                        json.getString("message")
                    } catch (e: Exception) {
                        "Login gagal (${response.code()})"
                    }
                    _loginState.postValue(LoginState.Error(errorMsg))
                }
            } catch (e: Exception) {
                _loginState.postValue(LoginState.Error("Koneksi gagal: ${e.localizedMessage}"))
            }
        }
    }

    private fun saveSession(response: LoginResponse) {
        PrefsHelper.get(getApplication()).edit().apply {
            putString("auth_token", "Bearer ${response.token}")
            putInt("juru_sungai_id", response.user.id)
            putString("juru_sungai_nama", response.user.nama)
            putInt("wilayah_sungai_id", response.user.wilayah_sungai_id)
            putLong("login_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    private suspend fun syncMasterData(token: String, wilayahSungaiId: Int) {
        try {
            val authHeader = "Bearer $token"
            val syncResponse = RetrofitClient.getApiService().syncMaster(authHeader)
            if (syncResponse.isSuccessful) {
                val syncBody = syncResponse.body()
                if (syncBody != null) {
                    val gson = Gson()
                    val jsonString = gson.toJson(syncBody)
                    val namaWilayah = syncBody.data.wilayah_sungai.find { it.id == wilayahSungaiId }?.nama_sungai ?: "-"

                    PrefsHelper.get(getApplication()).edit().apply {
                        putString("master_data", jsonString)
                        putString("nama_wilayah_sungai", namaWilayah)
                        apply()
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
}

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val response: LoginResponse) : LoginState()
    data class Error(val message: String) : LoginState()
}
