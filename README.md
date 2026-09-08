# Aplikasi OP Sungai — PUPR Kalimantan Selatan

Aplikasi Android offline-first untuk digitalisasi laporan inspeksi Operasi dan Pemeliharaan (OP) sungai oleh juru sungai di bawah Dinas PUPR Provinsi Kalimantan Selatan.

---

## Latar Belakang

Proses pelaporan inspeksi sungai selama ini dilakukan secara manual menggunakan formulir kertas (blangko), kemudian difoto dan dikirim ke kantor secara terpisah. Aplikasi ini menggantikan proses tersebut dengan sistem digital yang tetap bisa digunakan di lapangan tanpa koneksi internet, dan menyinkronkan data ke server secara otomatis saat koneksi tersedia.

## Fitur Utama

- **Offline-first** — data tersimpan di perangkat terlebih dahulu, sync otomatis saat ada koneksi internet
- **14 jenis bangunan** — mendukung OP-01.a hingga OP-01.n sesuai blangko resmi PUPR
- **Dokumentasi foto** — hingga 3 foto per bagian temuan, wajib minimal 1 foto termasuk untuk kondisi Normal
- **Status laporan** — DRAFT → LOCAL → SYNCED / FAILED, dengan indikator jelas di dashboard
- **Edit laporan draft** — juru bisa memperbaiki info umum dan kondisi per bangunan sebelum dikirim
- **Multi-bangunan per kunjungan** — 1 laporan kunjungan bisa mencakup beberapa bangunan berbeda
- **Kompresi foto otomatis** — resize maksimal 1280px dan target ukuran di bawah 300KB sebelum diunggah
- **Sinkronisasi otomatis** — berjalan di background via WorkManager, periodik setiap 15 menit dan bisa dipicu manual

## Tech Stack

### Android
| Komponen | Library |
|---|---|
| Bahasa | Kotlin |
| Database lokal | Room (SQLite) |
| HTTP client | Retrofit + OkHttp |
| Background sync | WorkManager |
| Image loading | Glide |
| UI | ViewBinding + Material Components |

### Backend
| Komponen | Teknologi |
|---|---|
| Framework | Laravel 12 |
| Database | MySQL |
| Auth | Laravel Sanctum |
| Dev environment | Laragon |

## Struktur Data

Hierarki data mengikuti struktur form inspeksi resmi:

```
laporan_op              → 1 kunjungan inspeksi
  └── item_inspeksi     → 1 per jenis bangunan yang diperiksa
        └── temuan_kondisi  → 1 per bagian bangunan
```

## Cara Menjalankan (Development)

### Prasyarat
- Android Studio Hedgehog atau lebih baru
- PHP 8.2+, Composer
- Laragon (MySQL)
- Device Android API 26+

### Setup Backend

```bash
cd river-op-api
cp .env.example .env
composer install
php artisan key:generate
php artisan migrate
php artisan db:seed
php artisan serve --host=0.0.0.0 --port=8000
```

Isi `.env`:
```env
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=river_op_db
DB_USERNAME=root
DB_PASSWORD=
```

### Setup Android

Alamat server **tidak lagi ditulis di dalam kode**. Build debug otomatis
menunjuk `http://127.0.0.1:8000/`, yang lewat kabel USB berarti laptopmu
sendiri. Jadi tidak ada yang perlu diedit tiap pindah jaringan.

1. Buka project di Android Studio
2. Colok HP ke laptop, nyalakan **USB debugging**
3. Buka terowongan USB-nya:
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```
4. Jalankan server Laravel di laptop (`php artisan serve`)
5. Run ke device

> Perintah `adb reverse` diulang tiap kali kabel dicabut atau adb restart.

**Kenapa lewat kabel, bukan wifi?** Banyak router — termasuk bawaan ISP dan
jaringan tamu — menyalakan *AP Isolation*, yang melarang perangkat di wifi yang
sama saling menghubungi. Gejalanya membingungkan: HP dan laptop satu jaringan,
IP-nya benar, tapi `ping` pun gagal dengan *Destination Host Unreachable*.
Lewat kabel, seluruh persoalan itu tidak ada.

#### Kalau tetap mau lewat wifi

Buat berkas `local.properties` di akar project (berkas ini diabaikan git,
jadi alamat laptopmu tidak ikut terkirim):

```properties
riverop.baseUrl=http://192.168.1.8:8000/
```

Ganti dengan IP laptopmu (`ipconfig` → IPv4 Address). Syaratnya AP Isolation
di router harus mati, dan port 8000 diizinkan Windows Firewall:

```
netsh advfirewall firewall add rule name="RiverOP dev 8000" dir=in action=allow protocol=TCP localport=8000
```

#### Build rilis

Build rilis **tidak punya alamat bawaan** dan akan menolak jalan sampai diisi:

```properties
riverop.baseUrl.release=https://alamat-server-dinas.go.id/
```

Wajib `https://`. Build debug boleh `http://` biasa lewat manifest terpisah di
`app/src/debug/`; build rilis tidak mendapat izin itu, sehingga sandi juru dan
isi laporan tidak mungkin terkirim tanpa enkripsi.

## Alur Penggunaan

```
Login
  ↓
Dashboard → Buat Laporan Baru
  ↓
Bagian 1: Info Umum (tanggal, cuaca, debit, foto lokasi)
  ↓
Bagian 2: Pilih Jenis Bangunan
  ↓
Bagian 3: Isi Kondisi per Bagian + Foto
  ↓
Simpan Draft (lanjut nanti) atau Kirim Laporan
  ↓
Sync ke Server (otomatis / manual)
  ↓
Status: SYNCED ✓
```

## Status Pengembangan

### Android
- [x] Autentikasi (login / logout)
- [x] Buat laporan baru
- [x] Simpan sebagai draft, lanjut di lain waktu
- [x] Edit laporan draft (info umum + kondisi per bangunan)
- [x] Tambah bangunan ke laporan yang sudah ada
- [x] Sinkronisasi ke server (foto + data laporan)
- [x] Tampilan detail laporan multi-bangunan
- [x] Kompresi foto otomatis sebelum upload
- [ ] Hapus data lokal otomatis setelah sync berhasil
- [ ] Mode edit kondisi per bagian (FormBagian3 edit mode) — dalam pengerjaan

### Backend
- [x] REST API autentikasi (Sanctum)
- [x] Endpoint sync data master
- [x] Endpoint upload foto
- [x] Endpoint sync laporan

### Web Dashboard
- [ ] Belum dimulai — direncanakan menggunakan Next.js

## Rencana Deployment Production

- VPS dengan domain resmi instansi
- HTTPS via Let's Encrypt
- Konfigurasi `APP_URL` di `.env` Laravel untuk URL foto yang konsisten

## Catatan untuk Developer

- Setiap perubahan skema Room (entity) harus disertai migration di `RiverOpDatabase.kt` dan uninstall app di device sebelum reinstall
- IP server di `RetrofitClient.kt` harus diperbarui setiap pindah jaringan (development only)
- Status DRAFT tidak akan pernah disync otomatis — hanya LOCAL dan FAILED yang diproses `SyncWorker`
- Nama bagian bangunan di form diambil dari master data server (bukan hardcoded) untuk menghindari mismatch

---

*Dikembangkan untuk keperluan internal Dinas PUPR Provinsi Kalimantan Selatan.*
