package com.pupr.kalsel.riverop.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.pupr.kalsel.riverop.BuildConfig
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.pupr.kalsel.riverop.MainActivity
import com.pupr.kalsel.riverop.databinding.ActivityLoginBinding
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import com.pupr.kalsel.riverop.utils.PrefsHelper

private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = PrefsHelper.get(this)
        val authToken = sharedPrefs.getString("auth_token", null)
        if (!authToken.isNullOrEmpty()) {
            val loginTimestamp = sharedPrefs.getLong("login_timestamp", 0L)
            val isExpired = loginTimestamp > 0L &&
                System.currentTimeMillis() - loginTimestamp > SESSION_DURATION_MS
            if (isExpired) {
                sharedPrefs.edit().clear().apply()
            } else {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
                return
            }
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    showLoading(true)
                }
                is LoginState.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "Login Berhasil", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                is LoginState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnLogin.setOnClickListener { cobaMasuk() }

        // Papan ketik menutupi tombol Masuk di layar kecil, sehingga juru harus
        // menutupnya dulu sebelum bisa menekan tombolnya. Tombol centang pada
        // papan ketik itu sendiri kini mengerjakan hal yang sama.
        binding.etPassword.setOnEditorActionListener { _, aksi, _ ->
            if (aksi == EditorInfo.IME_ACTION_DONE) {
                cobaMasuk()
                true
            } else {
                false
            }
        }

        // Versi ditulis dari BuildConfig, bukan diketik di tata letak, supaya
        // tidak pernah berbohong sesudah versinya naik.
        binding.tvVersi.text = "Versi ${BuildConfig.VERSION_NAME}"
    }

    private fun cobaMasuk() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (validateInput(username, password)) {
            viewModel.login(username, password)
        }
    }

    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.tilUsername.error = "Username tidak boleh kosong"
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password tidak boleh kosong"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false
            binding.etUsername.isEnabled = false
            binding.etPassword.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            binding.etUsername.isEnabled = true
            binding.etPassword.isEnabled = true
        }
    }
}