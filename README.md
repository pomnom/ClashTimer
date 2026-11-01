# Clash Timer

Aplikasi Android untuk memantau dan merencanakan upgrade Clash of Clans dari
**Export village data**, dengan notifikasi tepat waktu (alarm sistem Android).

## Fitur
**🧭 Tampilan**
- Navigasi bawah: **Desa · Beranda · Rencana · Progres**. Header satu baris: ‹ nama desa › (TH, jumlah
  builder bebas, "1/2 desa"), pilihan **Home | BB**, dan ikon **opsi desa**
- **Desa** (layar awal bila punya lebih dari satu desa): kartu tiap desa dengan kotak Builder, Lab,
  Builder Base & Star Lab — "2 bebas" + saran, atau hitung mundur upgrade yang paling cepat selesai —
  plus Goblin Builder/Researcher. Ketuk untuk membuka, tahan untuk ganti nama / hapus
- Warna tetap di semua layar: hijau = bebas, emas = builder sibuk, ungu = lab, merah = menganggur
- Tampilan bersih: penjelasan & tutorial ada di tombol **?** di samping judul bagian (Beranda, Rencana,
  Timeline, Kebutuhan, Builder ke-6, Clock Tower, Desa, Opsi desa, Progres, Pengaturan)

**🏠 Beranda**
- Hitung mundur besar "builder berikutnya bebas" + satu saran upgrade (dengan ikon)
- **Kotak pekerja**: B1…B6, Lab, Pet (atau M1…M3 & Star Lab di Builder Base) dan Goblin, masing-masing
  dengan hitung mundur, nama upgrade & bar progres; ketuk kotak bebas untuk memilih upgrade
- **Detail timer** (bisa dilipat): Builder, Laboratory, Pet House dengan cincin progres + ikon, waktu
  selesai, label helper/potion/event
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

**📅 Rencana** (antrean upgrade per pekerja)
- **Urutan tercepat ke target**: tiap strategi punya target — Seimbang = semua upgrade di TH sekarang
  (builder, lab & pet) selesai; Rush TH = Town Hall berikutnya naik (bangunan baru dipasang dulu, TH ikut
  direncanakan); Hero dulu = semua level hero selesai; Builder ke-6 = B.O.B terbuka; Max BB = Builder Base
  selesai. Setiap kali data atau pengaturan berubah (dan tiap 6 jam) aplikasi menyimulasikan beberapa cara
  mengurutkan — **rantai upgrade terpanjang dulu**, **upgrade terlama dulu**, rantai murni, dan cara lama
  (nilai ÷ √(lama × biaya)) — untuk tiap jenis pekerja, menguji gabungan terbaiknya, lalu memakai yang paling
  cepat mencapai target. Jam tidur/kerja, Gold Pass, event, CWL, helper & kapasitas gudang ikut dihitung;
  Laboratory / Pet House / Hero Hall / Star Lab didahulukan karena membuka level berikutnya
- Kartu **🎯 Target** di atas Rencana: tanggal target, selisihnya dengan urutan lama, dan urutan yang
  dipilih per pekerja. Saran di Beranda, widget & notifikasi memakai urutan yang sama
- Cara lama tetap ikut dicoba: hero yang menganggur didahulukan dan biaya Dark Elixir disetarakan
  (1 DE ≈ 100 Gold/Elixir)
- Builder Base: upgrade yang biayanya melebihi **kapasitas gudang** (Builder Hall + Gold/Elixir Storage)
  ditunda dan storage-nya dinaikkan dulu (juga untuk troop Star Lab); kapasitas tampil di Rencana BB
- Pilih strategi langsung di atas: Seimbang / Rush TH / Hero dulu, dan untuk Builder Base:
  **Builder ke-6** / Max
- Antrean per pekerja (B1–B6, M1–M3, Lab, Pet, Star Lab): sedang berjalan, hari ini, besok,
  minggu ini, nanti — ketuk untuk detail atau "Sudah dimulai"
- **Timeline** (pilihan Daftar | Timeline, diingat): satu baris per pekerja untuk 24 jam atau 3 hari —
  batang penuh = berjalan, transparan = rencana, garis merah = menganggur, abu-abu = jam tidur/offline,
  garis vertikal = sekarang. Ketuk batang untuk detail
- **Builder ke-6 (B.O.B)**: checklist 6 syarat (BH9 + B.O.B Control, 3 gear-up, troop Lv18,
  pertahanan Lv9, BM + BC = 45) dengan perkiraan tanggal tiap syarat dan tanggal B.O.B terbuka. Bangunan
  **dan jebakan** baru (mis. Mine, Mega Mine) dipasang dulu supaya Builder Hall bisa naik ke BH9
- Jam tidur/kerja, sesi helper, potongan event. Hero yang bentrok CWL **tidak dimasukkan ke saran & antrean**
  selama CWL — cukup disebut sekali di banner CWL (Beranda & Rencana), lalu dilanjutkan setelah CWL selesai

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
- Ukuran 4×2 (bisa diperbesar): satu baris per desa dengan kotak Builder, Lab, Builder Base &
  Star Lab — hitung mundur (< 24 jam) atau sisa hari/jam, **bebas** (kotak hijau), "max" bila tidak ada
  lagi yang bisa di-upgrade, "–" bila belum dibangun. Warna sama dengan aplikasi: emas = builder,
  ungu = lab
- Isinya sama dengan tab **Desa**: Goblin Builder/Researcher tidak dihitung sebagai pekerja tetap
  (tampil sebagai 👺 di bawah nama desa), dan saran memakai urutan tercepat yang sama dengan Rencana
- Desa yang paling cepat bebas di atas; perbesar widget untuk menampilkan lebih banyak desa.
  Baris bawah: saran untuk pekerja yang bebas, atau upgrade berikutnya yang selesai
- Tablet: huruf lebih besar, baris mengisi tinggi widget, dan nama upgrade (+ jumlah sibuk, mis. 4/5)
  tampil di bawah tiap waktu;
  saat mendatar jumlah baris menyesuaikan tinggi widget (ukuran ditentukan dari layar penuh, jadi tetap benar
  walau Clash Timer sedang dibuka di layar terbagi)
- Hitung mundur < 24 jam memakai format Android sesuai bahasa HP: "17.03" = 17 menit 3 detik,
  "16.27.11" = 16 jam 27 menit 11 detik
- Ketuk baris → Beranda desa itu; ketuk kotak Builder Base / Star Lab → Beranda Builder Base; ketuk judul
  "Semua desa" → tab Desa. Diperbarui saat data berubah (import, Sudah dimulai, API), saat
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
  Clock Tower, gear-up, Pet House / Star Lab, semua timer berjalan. **Boost yang sedang aktif** dibaca
  langsung dari export dan menggantikan catatan manual: Builder Potion (builder_boost), Research Potion
  (lab_boost), Pet Potion (pet_boost), Clock Tower (sisa boost & kapan siap lagi). Export hanya memberi
  sisa detik — bila Anda mencatat Builder Bite / Study Soup, jenis itu yang dipakai. Boost berikutnya tidak
  diasumsikan dipakai. **Goblin Builder / Goblin Researcher**
  (event, dibayar gem; "extra" di export) ditampilkan terpisah: tidak dihitung sebagai builder/lab tetap,
  upgrade-nya tetap jalan sampai selesai walau event berakhir, dan di rencana tidak diberi upgrade baru.
  B.O.B sudah terbuka → strategi Builder Base otomatis Max.
- **Gold Pass** dari lama timer: export & API tidak memuat status Gold Pass, jadi saat import aplikasi
  membandingkan upgrade yang ditandai **Sudah dimulai** atau yang baru dimulai sejak import sebelumnya
  dengan durasi aslinya (0 / 10 / 15 / 20 %, termasuk potongan event). Builder Boost & Research Boost
  terisi sendiri; bila baru satu yang terbukti, yang lain diperkirakan sama sampai ada buktinya.
  Upgrade Builder Base (Clock Tower), gear-up, upgrade yang dibantu helper & upgrade < 8 jam tidak dipakai.
- Dari API (setiap desa ditambah / di-update dan saat aplikasi dibuka): nama akun & clan, **ikut CWL atau
  tidak** (roster CWL clan, war preference, tidak di clan) beserta jadwal aslinya, awal & akhir musim
  Gold Pass, dan upgrade hero / lab / pet / Star Lab / TH / BH yang di game sudah naik level (book, gem,
  potion) langsung ditandai selesai.
- Tidak tersedia di export maupun API: jadwal event (Hammer Jam), book/hammer, dan potion yang dipakai
  setelah export — dicatat manual atau cukup export ulang.

**🔑 API resmi Clash of Clans** — Pengaturan → API resmi
- Buat key di developer.clashofclans.com → My Account → Create New Key, IP address
  **45.79.218.79** (proxy RoyaleAPI), salin Token, tempel di aplikasi
- Key hanya disimpan di HP; permintaan lewat cocproxy.royaleapi.dev

**⚙️ Opsi per desa** (ikon opsi di header, atau baris ringkas "💡 Seimbang · 🎫 20% · ⚔️ CWL" di Beranda)
- Lembar **Opsi desa**: strategi saran & collector dipilih sendiri; Gold Pass, CWL dan strategi
  Builder Base (setelah B.O.B) bertanda **otomatis** dengan penjelasan saat diketuk. Semua disimpan
  **per desa**.
- Chip di tab Rencana membuka pilihan yang sama. Lembar ini juga berisi Update desa, Potion & item,
  Ganti nama, dan Pengaturan aplikasi.
- Jam tidur/kerja, event, ikon, nada & notifikasi (⚙️ Pengaturan) berlaku untuk semua desa.

**⚔️ Pengingat serangan** (butuh API key)
- CWL (hari perang, bila akun ada di lineup), war biasa (war log clan harus publik) dan Raid Weekend:
  3 jam sebelum berakhir aplikasi mengecek API dan memberi notifikasi hanya bila serangan masih tersisa.
  Ketuk notifikasi → membuka Clash of Clans.

**📌 Prioritaskan / sembunyikan** — tahan lama upgrade di Beranda, Rencana, atau Progres
- Prioritaskan: didahulukan di saran, rencana, widget & notifikasi. Sembunyikan: hilang dari saran &
  rencana tetapi tetap dihitung di progres. Kelola di lembar **Opsi desa**.

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

**🔄 Update dari dalam aplikasi** (Pengaturan → Update aplikasi)
- Saat aplikasi dibuka (paling sering tiap 3 jam) Clash Timer mengecek rilis terbaru di GitHub
  (`build-N` = nomor versi aplikasi). Ada yang baru → kartu **Update** di layar utama
- Ketuk Update → APK diunduh lewat pengunduh Android (progres di aplikasi & notifikasi, checksum diperiksa)
  → ketuk notifikasi "Unduhan selesai" (atau Pasang → daftar Unduhan) → Update
- Clash Timer sengaja **tidak** punya izin memasang aplikasi: aplikasi yang bisa mengunduh sekaligus
  memasang APK ditandai Play Protect sebagai tidak aman. Pemasangan dilakukan aplikasi Unduhan/File HP
- File APK dihapus otomatis begitu build baru dibuka (atau setelah 3 hari bila tidak dipasang) — tidak
  memenuhi penyimpanan HP

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
4. Tunggu tab **Actions** selesai. Buka Clash Timer → kartu **⬆️ build baru tersedia → Update**
   (atau Pengaturan → Update aplikasi → Cek update sekarang): APK diunduh di dalam aplikasi, lalu ketuk
   notifikasi "Unduhan selesai" → Update. File-nya dihapus otomatis setelah build baru dibuka. Data tetap
   aman karena kunci tanda tangan sama. Mengunduh APK manual dari **Releases** tetap bisa.
- Folder `.github/workflows` tidak ikut diperbarui dari zip (token Actions tidak boleh mengubah file
  workflow) — bila workflow berubah, salin manual dari `.github/workflows/build-apk.yml`.
- Mengedit file langsung di GitHub (tanpa zip) juga langsung dibuatkan APK dari kode di repo.

## Cara pakai
1. Di game: Settings → More Settings → paling bawah **Export village data** → Copy.
2. Buka Clash Timer → **📋 Update desa** (atau Import dari Clipboard).
3. Pilih strategi saran & collector di lembar **Opsi desa** (ikon opsi di header); Gold Pass & CWL terisi
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
