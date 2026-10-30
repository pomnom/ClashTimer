# Clash Timer

Aplikasi Android untuk memantau dan merencanakan upgrade Clash of Clans dari
**Export village data**, dengan notifikasi tepat waktu (alarm sistem Android).

## Fitur
**🏠 Beranda**
- Hitung mundur besar "builder berikutnya bebas" + satu saran upgrade (dengan ikon)
- Builder, Laboratory, Pet House (dan Builder Base) dengan cincin progres + ikon, waktu selesai,
  label helper/potion/event
- **Sudah dimulai**: setelah memulai upgrade di game, ketuk tombol ini → timer & notifikasi
  langsung dipasang tanpa import ulang. **Lihat opsi** untuk memilih upgrade lain
- Ketuk sebuah upgrade → tandai selesai (gem/book) atau batal
- Helper (Apprentice, Lab Assistant, Alchemist), Potion & item yang layak dipakai sekarang,
  banner CWL & event, desa lain yang punya builder bebas
- **Builder Base**: Master Builder termasuk **gear-up di Home Village** ("Cannon · Gear-up"),
  kartu **Clock Tower** (catat boost gratis / Clock Tower Potion → timer BB dihitung 10×),
  ringkasan jalur builder ke-6

**📅 Rencana** (antrean seperti ClashClock)
- Pilih strategi langsung di atas: Seimbang / Rush TH / Hero dulu, dan untuk Builder Base:
  **Builder ke-6** / Max
- Antrean per pekerja (B1–B6, M1–M3, Lab, Pet, Star Lab): sedang berjalan, hari ini, besok,
  minggu ini, nanti — ketuk untuk detail atau "Sudah dimulai"
- **Builder ke-6 (B.O.B)**: checklist 6 syarat (BH9 + B.O.B Control, 3 gear-up, troop Lv18,
  pertahanan Lv9, BM + BC = 45) dengan perkiraan tanggal tiap syarat dan tanggal B.O.B terbuka
- Jam tidur/kerja, sesi helper, hero yang ditunda karena CWL, potongan event

**📊 Progres**
- Persentase menuju max TH/BH, perkiraan bulan selesai builder & lab, hari kerja tersisa
- Per kategori: persen, sisa biaya, daftar item (ketuk untuk membuka)

**🎉 Event** (Pengaturan → Event)
- Hammer Jam (−50% waktu & biaya, perkiraan 1–17 Nov), Builder Base Bonanza, atau event lain
- Tidak ada jadwal event resmi yang bisa dibaca aplikasi, jadi event diaktifkan & dicocokkan
  tanggalnya sendiri ("isi dari sisa waktu di game"). Potongan berlaku untuk upgrade yang
  dimulai selama event dan dikalikan dengan Gold Pass; opsi untuk ikut memotong upgrade berjalan

**🏰 Multi desa** · **🎵 Nada notifikasi** (termasuk Alarm keras) — seperti versi sebelumnya

**📱 Widget layar utama** (tahan lama layar HP → Widget → Clash Timer · desa)
- Ukuran 4×2 (bisa diperbesar): otomatis menampilkan desa yang pekerjanya (builder, lab, pet,
  Builder Base, Star Lab) **paling cepat bebas**
- Builder: hitung mundur builder berikutnya + jumlah bebas; Lab (+Pet) atau Star Lab; saran berikutnya;
  helper (Home) atau Clock Tower (Builder Base), dengan ikon bangunan
- Tombol 🏠/🛠 di pojok untuk ganti Home Village ↔ Builder Base (menampilkan kapan sisi lain bebas)
- Ketuk widget → buka tab Rencana desa itu. Diperbarui saat upgrade selesai, saat data berubah,
  dan tiap 30 menit; mengikuti tema terang/gelap HP

**⚙️ Opsi per desa — langsung di Beranda**
- Kartu **Opsi desa ini** di Beranda: strategi saran, Gold Pass (Builder & Research Boost), CWL
  (aktif/lama) dan collector; di sisi Builder Base: strategi BB (Builder ke-6 / Max), jumlah builder
  BB, Gold Pass, collector. Semua disimpan **per desa**; desa lama memakai nilai sebelumnya sebagai awal.
- Chip di tab Rencana membuka pilihan yang sama.
- Jam tidur/kerja, event, ikon, nada & notifikasi (⚙️ Pengaturan) berlaku untuk semua desa.

**Lainnya**
- Opsi **jangan upgrade collector** (Gold Mine, Elixir Collector, Dark Elixir Drill)
- Town Hall / Builder Hall baru disarankan setelah semua bangunan baru dipasang
- Gold Pass, jam tidur & kerja, opsi lewati hero saat CWL, mode alarm kuat

## Cara update dari HP (repo yang memakai ClashTimer.zip)
1. Buka repository di browser → **Add file → Upload files**.
2. Upload `ClashTimer.zip` yang baru (nama sama, menimpa yang lama) → **Commit changes**.
3. Tunggu tab **Actions** selesai, lalu unduh APK dari **Releases** dan install di atas
   versi lama (data tetap aman karena kunci tanda tangan sama).

## Cara pakai
1. Di game: Settings → More Settings → paling bawah **Export village data** → Copy.
2. Buka Clash Timer → **📋 Update desa** (atau Import dari Clipboard).
3. Atur Gold Pass, CWL & strategi di kartu **Opsi desa ini** (Beranda), lalu jam tidur/kerja,
   event, dan nada di ⚙️ **Pengaturan**.
4. Setelah memulai upgrade di game, tekan **Sudah dimulai** pada saran — atau import ulang.

## Catatan
- Timer = sisa waktu *saat export*. Potion/book/Clock Tower yang dipakai setelah export bisa
  dicatat; import ulang selalu menjadi sumber kebenaran.
- Perhitungan helper berulang adalah perkiraan (1 jam kerja, cooldown 23 jam, +level jam kerja).
- Clock Tower: 10× selama 12 + 2×level menit, cooldown 22 jam (data Supercell); tidak
  mempercepat gear-up.
- Data game (durasi, biaya, syarat) dari ClashKingAssets via coc-rush-helper, Oktober 2026.
- Ikon diunduh saat dipakai dari **ClashKing Assets** (https://assets.clashk.ing) — terima kasih!
  Bisa dimatikan di Pengaturan → Ikon. Izin INTERNET hanya dipakai untuk ini.

Fan content tidak resmi, tidak berafiliasi dengan dan tidak didukung oleh Supercell.
Gambar & nama Clash of Clans © Supercell.
