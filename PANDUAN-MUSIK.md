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

## Tambah lagu langsung dari game

1. Tekan **Right Shift**, lalu tekan **Daftar lagu**.
2. Ketik path folder atau file lagu di kotak bawah. Contoh: `/storage/emulated/0/Music`
3. Tekan **Tambah**. Lagu langsung masuk ke daftar tanpa restart Minecraft.

Jika path tidak ditemukan atau bukan MP3, OGG, atau WAV, status akan menjelaskan alasannya.

## Kontrol Dynamic Island

Pop-up di dalam game tidak bisa diklik, jadi tombolnya ada di layar kontrol:

1. Tekan **F7**, atau tekan Right Shift lalu **Kontrol Dynamic Island**.
2. Di kartu, ketuk tombol sebelumnya, jeda/putar, dan berikutnya. Ketuk bar progres untuk pindah posisi lagu.
3. **Berhenti**, **Ulang** (Mati, Semua, Satu), dan **Acak** ada di bawah kartu.
4. **Ukuran**, **Warna**, **Tampil saat jeda**, dan **Tampilan** (Kartu atau Pil) mengatur bentuk pop-up.
5. Geser posisinya di menu Right Shift dengan menahan klik kiri pada kotaknya.

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

## Menggeser HUD

1. Tekan **Right Shift** untuk membuka menu X FIVE LIVE.
2. Tahan klik kiri (atau jari di layar) pada kotak HUD yang ingin dipindah, lalu geser.
3. Lepas untuk menaruhnya. Posisi tersimpan otomatis.
4. Kotak yang disorot lebih terang saat kursor berada di atasnya. Yang bisa digeser: Musik atau Dynamic Island, Lirik, dan Chat.
5. Tombol **Reset posisi HUD** mengembalikan semua HUD ke posisi awal.

Tombol **Posisi lirik** tetap bisa dipakai untuk memilih Atas, Tengah, atau Bawah. Memilih posisi ini juga menghapus posisi lirik yang digeser manual.

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
- Tombol kontrol ada di layar F7 atau menu Right Shift, karena HUD di dalam game tidak bisa diklik.
- Pemutaran di Android belum diuji langsung di perangkat. Kalau tidak berbunyi, kirim status yang muncul di menu.
