# Aplikasi Juru Sungai — Sistem Informasi OP Sungai

Aplikasi Android *offline-first* untuk juru sungai Dinas PUPR Provinsi
Kalimantan Selatan. Menggantikan blangko kertas OP yang selama ini diisi di
lapangan, difoto, lalu dikirim ke kantor secara terpisah.

Bagian kedua dari sistem ini adalah dasbor web di repositori
[`khatami99/river-op`](https://github.com/khatami99/river-op), yang sekaligus
menjadi servernya.

---

## Prinsip yang menentukan bentuk aplikasi ini

**Seringan mungkin, tetapi tetap berfungsi.** Juru bekerja di tepi sungai
dengan HP kelas bawah dan sinyal yang sering hilang.

**Tidak ada satu pun pekerjaan lapangan yang boleh hilang.** Laporan ditulis ke
basis data di HP lebih dahulu, dan baru dikirim kemudian. Jaringan yang mati,
aplikasi yang tertutup, atau HP yang kehabisan daya tidak menghapus apa pun.

**Diam adalah kegagalan.** Kalau ada yang tidak beres, juru harus diberi tahu.
Laporan yang tertahan, sesi yang berakhir, dan server yang tidak terjangkau
masing-masing punya pesannya sendiri.

---

## Fitur

- **Bekerja tanpa jaringan.** Seluruh laporan tersimpan di HP; pengiriman
  berjalan sendiri di latar tiap 15 menit begitu ada jaringan.
- **14 jenis bangunan**, OP-01.a sampai OP-01.n, mengikuti blangko resmi PUPR.
  Daftar bagian bangunan diambil dari master data server, bukan ditulis di kode.
- **Foto sampai 3 per temuan**, dikecilkan sendiri ke maksimal 1280 px dan di
  bawah 300 KB sebelum dikirim.
- **Titik GPS per bangunan**, beserta ketelitiannya, dan bisa diulang bila gagal.
- **Beberapa bangunan dalam satu kunjungan**, dan tiap bangunan bisa diubah
  kembali selama laporannya belum terkirim.
- **Kabar hasil pengiriman** lewat notifikasi — berhasil, tertahan, atau sesi
  perlu diperbarui.
- **Uji koneksi server** dari layar masuk, membedakan empat keadaan yang
  penanganannya berbeda.

---

## Susunan data

```
laporan_op              satu kunjungan inspeksi
  └── item_inspeksi     satu per bangunan yang diperiksa
        └── temuan_kondisi   satu per bagian bangunan
```

Keadaan sebuah laporan: `DRAFT` → `LOCAL` → `SYNCING` → `SYNCED`, atau `FAILED`
bila ditolak server. Hanya `LOCAL` dan `FAILED` yang dijemput pekerja
sinkronisasi; `DRAFT` tidak pernah terkirim sampai juru menyelesaikannya.

---

## Teknologi

| | |
|---|---|
| Bahasa | Kotlin |
| Basis data lokal | Room (SQLite) |
| Jaringan | Retrofit + OkHttp |
| Pekerja latar | WorkManager |
| Gambar | Glide |
| Tampilan | ViewBinding + Material Components |
| Minimal | Android 8.0 (API 26) |

---

## Menjalankan untuk pengembangan

### Yang diperlukan

- Android Studio
- Server dari repositori `river-op` yang sedang berjalan
- HP Android 8.0+ dengan *USB debugging* menyala

### Lewat kabel USB — cara yang disarankan

Alamat server **tidak ditulis di dalam kode**. Build debug menunjuk
`http://127.0.0.1:8000/`, yang lewat kabel berarti laptopmu sendiri.

```bash
adb reverse tcp:8000 tcp:8000    # diulang tiap kabel dicabut
php artisan serve                # di repositori river-op
```

Lalu jalankan aplikasinya dari Android Studio.

**Kenapa kabel, bukan wifi?** Banyak router — termasuk bawaan ISP dan jaringan
tamu — menyalakan *AP Isolation*, yang melarang perangkat pada wifi yang sama
saling menghubungi. Gejalanya menyesatkan: IP benar, satu jaringan, tetapi
`ping` pun gagal. Lewat kabel persoalan itu tidak ada.

### Lewat wifi atau alamat lain

Buat `local.properties` di akar proyek — berkas ini diabaikan git:

```properties
riverop.baseUrl=http://192.168.1.8:8000/
```

Server harus dijalankan dengan `php artisan serve --host=0.0.0.0`, dan port 8000
diizinkan Windows Firewall.

### Mengganti alamat tanpa membangun ulang

Pada build debug, alamat server dapat diketik langsung dari aplikasi:
**layar masuk → Uji koneksi server**. Berguna ketika alamatnya berubah-ubah,
misalnya saat memakai terowongan sementara.

Build rilis mengunci kolom itu. Aplikasi yang boleh diarahkan ke server mana
saja bisa diarahkan ke server palsu.

---

## Membangun APK rilis

Perlu dua berkas yang keduanya diabaikan git.

**1. Alamat server produksi**, di `local.properties`:

```properties
riverop.baseUrl.release=https://alamat-dinas.go.id/
```

Wajib `https://`. Build rilis tidak punya alamat cadangan dan menolak berjalan
tanpa ini — supaya APK yang menunjuk laptop pribadi tidak pernah terbagikan.

**2. Kunci penanda tangan.** Dibuat sekali saja:

```bash
keytool -genkeypair -v -keystore riverop-rilis.jks -alias riverop -keyalg RSA -keysize 2048 -validity 10000
```

Lalu `keystore.properties` di akar proyek:

```properties
storeFile=riverop-rilis.jks
storePassword=...
keyAlias=riverop
keyPassword=...
```

> **Kunci ini tidak dapat dibuat ulang.** Bila berkasnya hilang atau sandinya
> lupa, aplikasi yang sudah terpasang di HP juru **tidak akan pernah bisa
> diperbarui** — harus dicopot lalu dipasang ulang, dan laporan yang belum
> terkirim ikut hilang. Simpan salinannya di tempat terpisah.

Sesudah keduanya ada:

```bash
./gradlew assembleRelease
```

---

## Alur pemakaian

```
Masuk
  ↓
Beranda → Buat Laporan Baru
  ↓
Bagian 1  info umum: tanggal, cuaca, debit, foto lokasi
  ↓
Bagian 2  pilih jenis bangunan
  ↓
Bagian 3  kondisi tiap bagian + foto + titik GPS
  ↓
Simpan Konsep  atau  Kirim Laporan
  ↓
Terkirim sendiri di latar begitu ada jaringan
```

---

## Catatan untuk pemelihara

**Perubahan skema Room** wajib disertai migrasi di `RiverOpDatabase.kt`. Basis
data di HP juru berisi pekerjaan lapangan yang tidak bisa diulang; menaikkan
versi tanpa migrasi akan menghapusnya.

**Alamat server tidak boleh kembali ke dalam kode.** Ia dibaca dari
`local.properties` lewat `build.gradle.kts`, dan `RetrofitClient` hanya
menerimanya.

**Nama bagian bangunan datang dari master data server**, bukan dari kode.
Menuliskannya di kode akan membuatnya berbeda dengan blangko resmi begitu dinas
memperbaruinya.

**Cleartext hanya untuk debug.** Izinnya berada di
`app/src/debug/AndroidManifest.xml` dan sengaja tidak diberikan ke build rilis.

**Foto dihapus sesudah terkirim, laporannya tidak.** Laporan berstatus `SYNCED`
tetap tersimpan sebagai riwayat karena juru sering perlu membukanya kembali di
lapangan; hanya berkas fotonya yang dibuang untuk menghemat penyimpanan. Ini
keputusan yang disengaja, bukan pekerjaan yang belum selesai.

---

*Dikembangkan untuk keperluan Dinas PUPR Provinsi Kalimantan Selatan.*
