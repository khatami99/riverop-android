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
import androidx.lifecycle.lifecycleScope
import com.pupr.kalsel.riverop.data.remote.RetrofitClient
import com.pupr.kalsel.riverop.databinding.ActivityLoginBinding
import com.pupr.kalsel.riverop.ui.dashboard.DashboardActivity
import com.pupr.kalsel.riverop.utils.PrefsHelper
import com.pupr.kalsel.riverop.utils.DiagnosaKoneksi
import com.pupr.kalsel.riverop.utils.SesiJuru
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SESSION_DURATION_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Batas menunggu jawaban server saat memeriksa sesi.
 *
 * Klien Retrofit menunggu tiga puluh detik, dan itu benar untuk mengirim
 * laporan — pekerjaan yang boleh lama asal berhasil. Tetapi di sini juru
 * sedang menatap layar pembuka: tiga puluh detik diam terbaca sebagai
 * aplikasi rusak, dan ia akan menutupnya sebelum sempat masuk. Enam detik
 * cukup untuk jaringan desa yang lambat tetapi hidup; lebih dari itu
 * dianggap tidak terjangkau, dan juru dipersilakan masuk apa adanya.
 */
private const val BATAS_PERIKSA_MS = 6_000L

class LoginActivity : AppCompatActivity() {

    companion object {
        /** Alasan juru dikeluarkan, dikirim dari layar yang mengeluarkannya. */
        const val EXTRA_ALASAN = "EXTRA_ALASAN"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    Toast.makeText(this, "Berhasil masuk.", Toast.LENGTH_SHORT).show()
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

        binding.tvUjiKoneksi.setOnClickListener { DiagnosaKoneksi.tampilkan(this) }

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

        // Alasan dari layar yang mengeluarkan juru menang atas pemeriksaan
        // apa pun: sesinya memang sudah dibuang di sana, tidak ada lagi yang
        // perlu ditanyakan ke server.
        val alasan = intent.getStringExtra(EXTRA_ALASAN)
        if (alasan != null) {
            tampilkanAlasan(alasan)
        } else {
            periksaSesiTersimpan()
        }
    }

    /**
     * Menentukan apakah juru boleh langsung masuk tanpa mengetik lagi.
     *
     * Sebelum ini keputusannya dibuat sendirian oleh HP: ada token, jamnya
     * belum lewat tiga puluh hari, masuk. Server tidak pernah ditanya sama
     * sekali. Akibatnya juru yang akunnya sudah dinonaktifkan dinas tetap
     * dipersilakan bekerja seharian, dan tidak satu pun laporannya sampai —
     * sesuatu yang baru ia ketahui ketika ditelepon.
     *
     * Kini server ditanya lebih dulu, tetapi HANYA jawaban "kamu ditolak" yang
     * mengeluarkannya. Jaringan mati, server tumbang, atau balasan aneh apa pun
     * tetap dianggap boleh masuk. Aturannya sengaja berat sebelah: aplikasi ini
     * dipakai di tepi sungai tanpa sinyal, dan mengunci juru di luar aplikasinya
     * sendiri hanya karena servernya sedang tidak terjangkau adalah kerusakan
     * yang jauh lebih besar daripada yang sedang dicegah.
     */
    private fun periksaSesiTersimpan() {
        val prefs = PrefsHelper.get(this)
        val token = prefs.getString("auth_token", null)

        if (token.isNullOrEmpty()) return

        val masuk = prefs.getLong("login_timestamp", 0L)
        if (masuk > 0L && System.currentTimeMillis() - masuk > SESSION_DURATION_MS) {
            SesiJuru.bersihkan(this)
            tampilkanAlasan("Masa berlaku sesi 30 hari telah berakhir. Silakan masuk kembali. "
                    + "Laporan yang belum terkirim tetap tersimpan.")
            return
        }

        tampilkanPemeriksaan(true)

        lifecycleScope.launch {
            val ditolak = withTimeoutOrNull(BATAS_PERIKSA_MS) {
                try {
                    RetrofitClient.getApiService().getProfil(token).code() == 401
                } catch (e: Exception) {
                    // Tidak terjangkau. Bukan penolakan.
                    false
                }
            } ?: false

            if (ditolak) {
                SesiJuru.bersihkan(this@LoginActivity)
                tampilkanPemeriksaan(false)
                tampilkanAlasan(
                    "Sesi pada perangkat ini sudah tidak berlaku. Silakan masuk kembali. "
                    + "Apabila tetap ditolak, hubungi petugas dinas."
                )
            } else {
                keDasbor()
            }
        }
    }

    private fun keDasbor() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun tampilkanPemeriksaan(sedang: Boolean) {
        binding.grupMasuk.visibility = if (sedang) View.INVISIBLE else View.VISIBLE
        binding.tvMemeriksa.visibility = if (sedang) View.VISIBLE else View.GONE
        binding.progressBar.visibility = if (sedang) View.VISIBLE else View.GONE
    }

    private fun tampilkanAlasan(pesan: String) {
        binding.tvAlasan.text = pesan
        binding.tvAlasan.visibility = View.VISIBLE
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
            binding.tilUsername.error = "Nama pengguna wajib diisi."
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Kata sandi wajib diisi."
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