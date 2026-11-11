# Panduan Musik X FIVE LIVE (Android, Zalith Launcher)

Panduan ini untuk memasukkan lagu pribadi secara manual, tanpa Spotify atau YouTube.

## Langkah 1: Buka folder game di Zalith

1. Buka aplikasi **Zalith Launcher**.
2. Pilih instance Minecraft yang sudah ada mod X FIVE LIVE-nya.
3. Cari pengaturan folder game (sering disebut **Game directory** atau **Folder game**).
   Lokasinya bisa berbeda di tiap versi Zalith. Kalau tidak ketemu, cek pengaturan instance dan pengaturan umum Zalith.

## Langkah 2: Buka folder xfive-music

1. Buka game sekali dengan mod ini. Folder **xfive-music** akan dibuat otomatis di dalam folder game.
2. Keluar dari game, lalu buka aplikasi **File** (atau File Manager) di HP.
3. Masuk ke folder game dari Langkah 1, lalu buka folder **xfive-music**.

Kalau folder itu belum ada, buat sendiri dengan nama persis `xfive-music` (huruf kecil semua).

## Langkah 3: Salin lagu ke dalam folder

1. Salin file lagu ke dalam folder **xfive-music**.
2. Format yang didukung: **.mp3**, **.ogg**, dan **.wav**.
3. Beri nama file dengan pola **Artis - Judul.mp3**, misalnya `Adele - Hello.mp3`.
   Dengan pola ini, nama artis dan judul tampil di HUD.
4. (Opsional) Lirik bisa ditaruh di file **Artis - Judul.lrc** di folder yang sama.

### Cara memasukkan lagu dari file ZIP

1. Unduh lagu dalam bentuk ZIP seperti biasa.
2. Buka aplikasi **File**, lalu ekstrak (unzip) file ZIP tersebut. Hasilnya berupa file .mp3, .ogg, atau .wav.
3. Salin file-file lagu itu ke folder **xfive-music** (lihat Langkah 2).
4. Buka game, lalu tekan **Right Shift**. Daftar lagu diperbarui otomatis setiap menu dibuka, jadi **tidak perlu restart atau muat ulang Minecraft**.
5. Di menu, perhatikan baris **Folder lagu** dan **Jumlah lagu**. Baris itu menampilkan lokasi folder yang sedang dipakai dan berapa lagu yang terbaca.

Mod tidak bisa membuka pemilih file dari HP. Karena itu lagu harus disalin manual ke folder yang ditampilkan di menu.

## Langkah 4: Putar lagu di dalam game

1. Buka game, lalu tekan **Right Shift** untuk membuka menu X FIVE LIVE.
2. Lagu baru otomatis terbaca. Tidak perlu restart atau muat ulang Minecraft.
3. Tekan **Putar / Jeda** untuk memutar musik.
4. Atur **Volume** dengan slider di kolom kiri.
5. Tekan **Lagu berikutnya** atau **Lagu sebelumnya** untuk pindah lagu.
6. Tekan **Tutup** untuk keluar dari menu. Pengaturan akan tersimpan otomatis.

## Menu X FIVE LIVE (semua dalam satu jendela)

Tekan **F7** (lagu) atau **Right Shift** (tampilan). Mouse otomatis aktif saat jendela terbuka. Satu jendela ini berisi semuanya:

- **Lagu**: daftar lagu bergaya playlist. Geser dengan jari, ketuk lagu untuk memutar, ketik di kotak cari. Tekan **+ Tambah**, isi path folder atau file lagu (contoh `/storage/emulated/0/Music`), lalu **Simpan**.
- **Tampilan**: nyalakan atau matikan HUD, Dynamic Island, lirik, chat; ganti tema dan warna; atur transparansi, lebar dan tinggi Island, lama tampil, ukuran lirik.
- **Audio**: volume, ulang, acak, berhenti, putar otomatis, segarkan daftar lagu.
- **Edit HUD**: geser kotak HUD dengan jari. Tarik titik kecil di pojok kanan bawah Island untuk mengubah ukuran. Tekan **Selesai** jika sudah.
- **Bar pemutar di bawah**: sebelumnya, putar/jeda, berikutnya, progres lagu (ketuk atau geser), acak, ulang, dan volume.

Tekan **Tutup** atau Esc untuk keluar. Semua pengaturan tersimpan otomatis.

## Dynamic Island

Saat lagu mulai, berganti, atau dijeda, kapsul kecil muncul di atas layar, membuka menjadi kartu, lalu menutup sendiri setelah beberapa detik. Di halaman **Tampilan** kamu bisa mematikan **Auto sembunyi** agar kartu tetap tampil, memilih bentuk Kartu atau Pil, dan mengatur lama tampil.

Tombol putar, berikutnya, sebelumnya, berhenti, dan ulang juga bisa dipasang di **Options → Controls**, kategori X FIVE LIVE. Secara bawaan belum dipasang.

## Opsional: lagu dari folder lain

Kalau lagu ada di folder lain di HP (misalnya Music atau Download):

1. Buka **xfive-music**, lalu buat file teks bernama **folder-musik.txt**.
   File ini dibuat otomatis saat game dibuka, jadi cukup dibuka dan diedit.
2. Tulis satu path per baris. Contoh:
   ```
   /storage/emulated/0/Music
   /storage/emulated/0/Download/lagu.mp3
   ```
3. Baris yang diawali tanda **#** diabaikan.
4. Simpan, lalu buka game dan tekan **Right Shift**. Lagu dari folder tambahan akan terbaca.

Catatan: cara paling mudah dan paling pasti adalah menyalin lagu langsung ke folder **xfive-music**.

## Kalau musik tidak berbunyi

- Pastikan formatnya benar-benar MP3, OGG, atau WAV. Mengganti nama file dari .m4a atau .webm menjadi .mp3 tidak membuat file itu jadi MP3. Kalau status menulis "M4A" atau "WebM", ubah file itu dengan aplikasi konverter ke MP3 atau OGG.
- Kalau status menulis "tidak berisi audio", file-nya kemungkinan rusak atau tidak lengkap. Coba salin ulang dari sumbernya.
- Coba dulu dengan lagu pendek (1 sampai 3 menit). Lagu sangat panjang butuh memori besar.
- Lihat tulisan status di menu X FIVE LIVE. Di sana tertulis alasannya, misalnya "Format tidak didukung" atau "Belum ada lagu".
- Baris **Status** di menu (Right Shift) menampilkan kondisi terakhir. Jika tertulis "Gagal", pesan setelahnya menjelaskan penyebabnya.
- Cek volume di tiga tempat:
  1. Volume HP (tombol volume samping, dan pastikan tidak dalam mode senyap).
  2. Di Minecraft, buka **Options → Music & Sounds** dan pastikan **Master Volume** tidak nol.
  3. Slider **Volume** di menu X FIVE LIVE.

## Batasan saat ini

- Tidak ada koneksi ke Spotify atau YouTube. Musik hanya berasal dari file di HP.
- HUD di dalam game tidak bisa diklik (kursor terkunci). Tekan F7 untuk membuka menu dan mengendalikan musik.
- Pemutaran di Android belum diuji langsung di perangkat. Kalau tidak berbunyi, kirim status yang muncul di menu.
