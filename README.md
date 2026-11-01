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
- Upgrade yang dibantu helper berulang: waktu utama sudah memperhitungkan sesi helper berikutnya (otomatis
  tiap hari), jadi lebih cepat dari timer di game; baris **di game** menampilkan sisa waktu seperti di game
- **Builder Base**: Master Builder termasuk **gear-up di Home Village** ("Cannon · Gear-up"),
  kartu **Clock Tower** (catat boost gratis / Clock Tower Potion → timer BB dihitung 10×),
  ringkasan jalur builder ke-6

**📅 Rencana** (antrean seperti ClashClock)
- Cara memilih (Seimbang): nilai upgrade dibagi √(lama × biaya), dikurangi bila builder menganggur saat
  tidur. **Hero yang menganggur selalu didahulukan** saat ada builder bebas (kecuali bentrok CWL), dan
  biaya Dark Elixir disetarakan (1 DE ≈ 100 Gold/Elixir) supaya hero DE tidak dianggap murah
- Builder Base: upgrade yang biayanya melebihi **kapasitas gudang** (Builder Hall + Gold/Elixir Storage)
  ditunda dan storage-nya dinaikkan dulu (juga untuk troop Star Lab); kapasitas tampil di Rencana BB
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

**📱 Widget layar utama** (tahan lama layar HP → Widget → Clash Timer · semua desa)
- Ukuran 4×2 (bisa diperbesar): satu baris per desa dengan kolom Builder, Lab, Builder Base &
  Star Lab — hitung mundur (< 24 jam) atau sisa hari/jam, **bebas** (hijau), "max" bila tidak ada
  lagi yang bisa di-upgrade, "–" bila belum dibangun
- Desa yang paling cepat bebas di atas; perbesar widget untuk menampilkan lebih banyak desa.
  Baris bawah: saran untuk pekerja yang bebas, atau upgrade berikutnya yang selesai
- Tablet: huruf lebih besar, baris mengisi tinggi widget, dan nama upgrade tampil di bawah tiap waktu;
  saat mendatar jumlah baris menyesuaikan tinggi widget (ukuran ditentukan dari layar penuh, jadi tetap benar
  walau Clash Timer sedang dibuka di layar terbagi)
- Hitung mundur < 24 jam memakai format Android sesuai bahasa HP: "17.03" = 17 menit 3 detik,
  "16.27.11" = 16 jam 27 menit 11 detik
- Ketuk baris → tab Rencana desa itu. Diperbarui saat data berubah (import, Sudah dimulai, API), saat
  upgrade selesai, saat keluar dari aplikasi, dan paling lambat tiap 30 menit; mengikuti tema terang/gelap
- **↻ jam** di pojok kanan atas = terakhir diperbarui; ketuk untuk memperbarui sekarang
- Setiap pembaruan menggambar widget dari awal, jadi tampilan baru langsung terpakai setelah update aplikasi
  (sebelum pembaruan pertama widget hanya menampilkan "Memuat…", bukan data contoh)
- **Xiaomi / HyperOS**: Info aplikasi → aktifkan **Mulai otomatis** dan **Penghemat baterai → Tidak ada
  batasan**, supaya widget & notifikasi tetap diperbarui walau aplikasi ditutup (kartu petunjuk di Beranda,
  juga Pengaturan → Widget & berjalan di latar)
- Data tiap perangkat terpisah: update desa di HP tidak mengubah widget di tablet (pakai **Pindah data**)
- Widget lama "Clash Timer · desa" sudah dihapus; bila masih ada di layar, hapus lalu pasang widget ini

**🔔 Notifikasi interaktif**
- "Upgrade selesai" menampilkan saran berikutnya untuk pekerja itu + tombol **Sudah dimulai**
  (timer langsung dipasang tanpa membuka aplikasi)
- Pengingat **Clock Tower siap lagi** (22 jam setelah boost gratis selesai), **event dimulai** &
  **berakhir besok**, dan **Gold Pass berakhir besok** (bila API diisi)

**📋 Update cepat**
- Setelah Copy export di game lalu membuka aplikasi: kartu "Export baru untuk … — Update"
- Tekan lama ikon aplikasi → **Update desa**, atau tile Quick Settings **Update desa**

**💰 Kebutuhan sumber daya**
- Tab Rencana: total Gold/Elixir/DE (dan Gold/Elixir BB) untuk upgrade yang dimulai dalam
  24 jam / 3 hari / 7 hari + upgrade termahal tiap sumber daya. Beranda: ringkasan 24 jam

**🔎 Terdeteksi otomatis (tanpa pengaturan)**
- Dari export: jumlah builder (termasuk B.O.B), builder Builder Base, helper & cooldown-nya, level
  Clock Tower, gear-up, Pet House / Star Lab, semua timer berjalan. **Clock Tower**: boost yang sedang
  berjalan dan kapan boost gratis siap lagi dibaca langsung dari export (menggantikan catatan manual;
  boost berikutnya tidak diasumsikan dipakai). B.O.B sudah terbuka → strategi
  Builder Base otomatis Max.
- **Gold Pass** dari lama timer: export & API tidak memuat status Gold Pass, jadi saat import aplikasi
  membandingkan upgrade yang ditandai **Sudah dimulai** atau yang baru dimulai sejak import sebelumnya
  dengan durasi aslinya (0 / 10 / 15 / 20 %, termasuk potongan event). Builder Boost & Research Boost
  terisi sendiri; bila baru satu yang terbukti, yang lain diperkirakan sama sampai ada buktinya.
  Upgrade Builder Base (Clock Tower), gear-up, upgrade yang dibantu helper & upgrade < 8 jam tidak dipakai.
- Dari API (setiap desa ditambah / di-update dan saat aplikasi dibuka): nama akun & clan, **ikut CWL atau
  tidak** (roster CWL clan, war preference, tidak di clan) beserta jadwal aslinya, awal & akhir musim
  Gold Pass, dan upgrade hero / lab / pet / Star Lab / TH / BH yang di game sudah naik level (book, gem,
  potion) langsung ditandai selesai.
- Tidak tersedia di export maupun API: jadwal event (Hammer Jam), potion/book yang dipakai — tetap dicatat
  manual.

**🔑 API resmi Clash of Clans** — Pengaturan → API resmi
- Buat key di developer.clashofclans.com → My Account → Create New Key, IP address
  **45.79.218.79** (proxy RoyaleAPI), salin Token, tempel di aplikasi
- Key hanya disimpan di HP; permintaan lewat cocproxy.royaleapi.dev

**⚙️ Opsi per desa — langsung di Beranda**
- Kartu **Opsi desa ini**: strategi saran & collector dipilih sendiri; Gold Pass, CWL dan strategi
  Builder Base (setelah B.O.B) bertanda **otomatis** dengan penjelasan saat diketuk. Semua disimpan
  **per desa**.
- Chip di tab Rencana membuka pilihan yang sama.
- Jam tidur/kerja, event, ikon, nada & notifikasi (⚙️ Pengaturan) berlaku untuk semua desa.

**⚔️ Pengingat serangan** (butuh API key)
- CWL (hari perang, bila akun ada di lineup), war biasa (war log clan harus publik) dan Raid Weekend:
  3 jam sebelum berakhir aplikasi mengecek API dan memberi notifikasi hanya bila serangan masih tersisa.
  Ketuk notifikasi → membuka Clash of Clans.

**📌 Prioritaskan / sembunyikan** — tahan lama upgrade di Beranda, Rencana, atau Progres
- Prioritaskan: didahulukan di saran, rencana, widget & notifikasi. Sembunyikan: hilang dari saran &
  rencana tetapi tetap dihitung di progres. Kelola di kartu **Opsi desa ini**.

**📈 Riwayat & statistik** (tab Progres)
- Grafik % menuju max per minggu, jam builder menganggur per minggu (Home / Builder Base) dan upgrade
  selesai per bulan — dikumpulkan dari setiap import.
- Statistik akun dari API: trophy & liga, war stars, donasi, Clan Capital, achievements, info clan,
  super troop aktif (juga di Beranda).

**🔮 Simulasi** (tab Progres → Hitung simulasi)
- Tanggal bangunan, hero & lab selesai (dan Builder Base max) untuk: dengan/tanpa Gold Pass, Hammer Jam,
  tiga strategi saran, dan builder ke-6.

**⚒️ Kalkulator ore** (tab Progres) — level equipment dari export/API, batas Blacksmith & TH, ore yang
  dimiliki + pemasukan per minggu → kebutuhan Shiny/Glowy/Starry Ore & perkiraan minggu.

**🧱 Tembok** — progres terpisah (Home & Builder Base), biaya naik satu level per level tembok, dan total ke max.

**📦 Pindah data & cadangan** (Pengaturan) — Salin semua data / Tempel data (HP ↔ tablet) atau simpan /
  buka file cadangan .json: desa, timer, opsi, riwayat & API key.

**🖥️ Layar lebar** — di tablet atau HP landscape, Beranda dan Rencana tampil berdampingan.

**Data game tambahan**
- Jebakan Builder Base (Push Trap, Mine, Mega Mine, Spring Trap) ikut saran & progres BB.
- Senjata Town Hall: sejak update Oktober 2025 level senjata TH12–15 dihapus Supercell; yang masih punya
  level (TH17 Inferno Artillery) ikut saran & progres.
- Crafting Station: level modul tiap pertahanan rakitan musim ini (tab Progres).

**Lainnya**
- Opsi **jangan upgrade collector** (Gold Mine, Elixir Collector, Dark Elixir Drill)
- Town Hall / Builder Hall baru disarankan setelah semua bangunan baru dipasang
- Jam tidur & kerja, lewati hero saat CWL (otomatis dengan API), mode alarm kuat

## Cara update dari HP (repo yang memakai ClashTimer.zip)
1. Buka repository di browser → **Add file → Upload files**.
2. Upload `ClashTimer.zip` yang baru → **Commit changes**.
3. GitHub Actions otomatis mengekstrak zip ke repo (README, kode & resource ikut terbarui; file yang
   sudah tidak ada di zip ikut terhapus), menghapus zip-nya, lalu membuat APK.
4. Tunggu tab **Actions** selesai, lalu unduh APK dari **Releases** dan install di atas versi lama
   (data tetap aman karena kunci tanda tangan sama).
- Folder `.github/workflows` tidak ikut diperbarui dari zip (token Actions tidak boleh mengubah file
  workflow) — bila workflow berubah, salin manual dari `.github/workflows/build-apk.yml`.
- Mengedit file langsung di GitHub (tanpa zip) juga langsung dibuatkan APK dari kode di repo.

## Cara pakai
1. Di game: Settings → More Settings → paling bawah **Export village data** → Copy.
2. Buka Clash Timer → **📋 Update desa** (atau Import dari Clipboard).
3. Pilih strategi saran & collector di kartu **Opsi desa ini** (Beranda); Gold Pass & CWL terisi
   sendiri. Jam tidur/kerja, event, nada, dan API key ada di ⚙️ **Pengaturan**.
4. Setelah memulai upgrade di game, tekan **Sudah dimulai** pada saran — atau import ulang
   (sekaligus membuat Gold Pass terdeteksi).

## Catatan
- Timer = sisa waktu *saat export*. Potion/book/Clock Tower yang dipakai setelah export bisa
  dicatat; import ulang selalu menjadi sumber kebenaran.
- Perhitungan helper berulang adalah perkiraan (1 jam kerja, cooldown 23 jam, +level jam kerja).
- Clock Tower: 10× selama 12 + 2×level menit, cooldown 22 jam sejak boost selesai (data Supercell); tidak
  mempercepat gear-up.
- Data game (durasi, biaya, syarat) dari ClashKingAssets via coc-rush-helper, Oktober 2026.
- Ikon diunduh saat dipakai dari **ClashKing Assets** (https://assets.clashk.ing) — terima kasih!
  Bisa dimatikan di Pengaturan → Ikon. Izin INTERNET hanya dipakai untuk ini.

Fan content tidak resmi, tidak berafiliasi dengan dan tidak didukung oleh Supercell.
Gambar & nama Clash of Clans © Supercell.
