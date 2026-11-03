# Clash Timer

Aplikasi Android untuk memantau dan merencanakan upgrade Clash of Clans dari
**Export village data**, dengan notifikasi tepat waktu (alarm sistem Android).

## Fitur
**🧭 Tampilan**
- Navigasi bawah (rel di kiri pada tablet): **Dashboard · Tukang · Lab · Progres**. Semua halaman
  hanya untuk **desa yang dipilih**. Header: nama desa ▾ (ketuk = lembar **Desa anda**), TH/BH & persen
  menuju max, ikon update dari export & opsi desa, lalu **Home Village | Builder Base** (di Tukang & Lab:
  **Agenda/Antrian | Timeline** dan **Home | BB**)
- **Desa anda** (lembar pemilih desa, menggantikan tab Desa): login berikutnya semua desa, lalu kartu tiap
  desa dengan kotak Tukang, Lab, Builder Base & Star Lab (hitung mundur / bebas). Ketuk desa = buka,
  ketuk kotak = langsung ke halaman Tukang / Lab-nya, tahan = ganti nama / opsi / hapus
- Tema **ikut sistem / gelap / terang** (Pengaturan → Umum), satu sistem warna: hijau = bebas / tindakan, kuning = builder,
  ungu = lab, oranye = menganggur / peringatan. Angka & hitung mundur memakai huruf lebar tetap
- Satu tombol utama per kartu; detail & aksi lain di **lembar bawah**. Penjelasan & tutorial di tombol **?**
- Changelog: kartu update menampilkan catatan rilis GitHub (bila ada), dan "Yang baru" tampil sekali
  setelah update (juga di Pengaturan → Update aplikasi)

**🌐 Bahasa** — Bahasa Indonesia (bawaan) atau **English** di Pengaturan → Umum → Bahasa. Halaman utama,
  rencana, notifikasi & widget sudah diterjemahkan; teks bantuan dan sebagian layar lain masih berbahasa Indonesia.

**⚙️ Pengaturan** — dirombak per kategori: Umum (bahasa, tema, ikon), Jam istirahat, Notifikasi, Event,
  Data & API, Widget & latar, Tentang & update. Strategi, Gold Pass & CWL tetap per desa (Opsi desa).

**🔕 Senyap saat jam istirahat** (Pengaturan → Jam istirahat)
- Upgrade yang selesai di jam tidur masuk ke panel **tanpa suara, getar, maupun alarm**; pengingat
  "sebentar lagi" dilewati. Alarm keras yang masih berbunyi dihentikan begitu jam tidur mulai
- Bisa juga untuk jam kerja / sekolah. Saat jam istirahat selesai: satu ringkasan bersuara (bisa dimatikan,
  dan tidak dikirim bila aplikasi sudah dibuka). Tes nada tetap berbunyi

**🏠 Dashboard**
- **Ringkasan strategi**: strategi & target ("Seimbang · Max TH11 ±7 Feb 2027"), tukang vs lab mana yang
  **penentu**, lalu pil Gold Pass (+ kenaikan berikutnya), event, CWL dan boost yang aktif. Ketuk = opsi desa
- **Siap dipakai** (paling atas, seperti "Data desa ketinggalan"): Builder's Apprentice, Lab Assistant, Alchemist dan
  boost gratis Clock Tower begitu bisa dipakai — dengan saran upgrade yang dibantu (terlama). Ketuk setelah memakainya
  di game: cooldown 23 jam & pengingatnya dihitung dari situ. Helper yang otomatis membantu upgrade yang sama tidak muncul
- **War / CWL** (butuh API key): serangan hari ini sudah dipakai atau belum. Setelah menyerang (atau tidak masuk
  lineup), upgrade hero yang selesai ≥ 3 jam sebelum hari perang berikutnya berakhir ikut saran & rencana; hari
  terakhir CWL sudah diserang → hero bebas. Ketuk kartu = cek ulang API
- **Hitung mundur** tukang & lab yang paling cepat selesai (jam:menit:detik), dengan pekerja yang bebas
- **Tukang** dan **Lab** yang sedang bekerja + **satu saran**: kartu saran untuk pekerja yang bebas
  (**Sudah dimulai** / **Opsi lain**, dengan **Batalkan** di pesan bawah), atau "saran berikutnya" saat
  pekerja tercepat bebas, dengan lonceng pengingat. "Lihat semua" membuka halaman Tukang / Lab
- **Baru selesai** dengan tombol ✓, **Helper**, **Potion & item** yang layak dipakai (BB: kartu Clock Tower)
- Ketuk upgrade berjalan → lembar detail (progres, sisa, waktu di game, tandai selesai, potion, **ubah sisa
  waktu** seperti di game — berlaku sampai import berikutnya, prioritaskan, batalkan)
- Upgrade yang dibantu helper berulang: waktu utama sudah memperhitungkan sesi helper berikutnya (otomatis
  tiap hari), jadi lebih cepat dari timer di game; "Di game" di lembar detail = sisa waktu seperti di game

**🔨 Tukang** (builder / Master Builder)
- **Bebas sekarang**: satu kartu per builder yang bebas — saran teratas (urutan tercepat ke target), lama &
  biaya, dan akibatnya: hijau "selesai 06:25 · pas bangun", oranye "menganggur 3j saat tidur"
- **Agenda** (bawaan): sesi login sampai bangun berikutnya — jam, "sblm tidur" / "bangun", isi tiap sesi dan
  lonceng pengingat (**satu pengingat per sesi login**); pita jam tidur menunjukkan builder yang menganggur.
  Lonceng aktif: notifikasi "selesai" per upgrade di sesi itu masuk tanpa suara
- **Timeline**: satu baris per builder untuk 24 jam atau 3 hari; ketuk batang → kartu detail (mulai, lama,
  selesai, alasannya, "Sudah dimulai" atau "Ingatkan pukul …")
- **Sedang dikerjakan**: kartu per builder (tercepat di atas) dengan hitung mundur, bar, dan apa yang terjadi
  setelahnya ("lalu Air Sweeper → 6" atau "menganggur ±4 jam")
- **Antrian** besok dan seterusnya, per hari; peringatan builder menganggur 7 hari ke depan
- **Builder Base**: Master Builder termasuk **gear-up di Home Village**; kapasitas gudang BB

**🧪 Lab** (Laboratory, Pet House / Star Lab)
- Kartu riset yang berjalan (termasuk Goblin Researcher), **Antrian** riset berikutnya dengan lonceng
  pengingat, atau **Timeline** lab, lalu **Sisa riset** per kategori (ketuk → Progres)

**🎯 Rencana**
- **Urutan tercepat ke target**: tiap strategi punya target — Seimbang = semua upgrade di TH sekarang
  (builder, lab & pet) selesai; Rush TH = Town Hall berikutnya naik (bangunan baru dipasang dulu, TH ikut
  direncanakan); Hero dulu = semua level hero selesai; Builder ke-6 = B.O.B terbuka; Max BB = Builder Base
  selesai. Setiap kali data atau pengaturan berubah (dan tiap 6 jam) aplikasi menyimulasikan beberapa cara
  mengurutkan — **rantai upgrade terpanjang dulu**, **upgrade terlama dulu**, rantai murni, dan cara lama
  (nilai ÷ √(lama × biaya)) — untuk tiap jenis pekerja, menguji gabungan terbaiknya, lalu memakai yang paling
  cepat mencapai target. Jam tidur/kerja, Gold Pass, event, CWL, helper & kapasitas gudang ikut dihitung;
  Laboratory / Pet House / Hero Hall / Star Lab didahulukan karena membuka level berikutnya
- **Urutan prioritas**: (1) tercepat ke target; bila sama cepat, (2) jeda menganggur terpanjang paling pendek,
  lalu (3) total menganggur paling sedikit — untuk semua builder, lab, pet, Master Builder & Star Lab.
  Menganggur karena semua sudah max atau menunggu syarat tidak dihitung. Ada cara baru **"pas jam tidur"**:
  memilih upgrade yang selesainya pas saat bangun
- **Lab & Star Lab tidak menunggu builder**: gudang atau upgrade Laboratory/Star Lab yang membuka riset
  berikutnya didahulukan, dan Star Lab mengisi riset lain bila riset fokus belum bisa
- **Semua hero jalan bersamaan**; saat builder kehabisan pekerjaan tetapi lab/pet masih jalan, strategi
  Seimbang/Max memulai **Town Hall berikutnya lebih awal**
- Builder ke-6 memakai **jalur kritis** (syarat dengan sisa waktu terpanjang dulu). Max BB di BH9: buka
  B.O.B dulu, lalu lanjut dengan 4 Master Builder
- Saran di Dashboard, Tukang, Lab, widget & notifikasi memakai urutan yang sama
- Cara lama tetap ikut dicoba: hero yang menganggur didahulukan dan biaya Dark Elixir disetarakan
  (1 DE ≈ 100 Gold/Elixir)
- Builder Base: upgrade yang biayanya melebihi **kapasitas gudang** (Builder Hall + Gold/Elixir Storage)
  ditunda dan storage-nya dinaikkan dulu (juga untuk troop Star Lab); kapasitas tampil di Tukang BB
- **Builder ke-6 (B.O.B)**: checklist 6 syarat (BH9 + B.O.B Control, 3 gear-up, troop Lv18,
  pertahanan Lv9, BM + BC = 45) dengan perkiraan tanggal tiap syarat dan tanggal B.O.B terbuka. Bangunan
  **dan jebakan** baru (mis. Mine, Mega Mine) dipasang dulu supaya Builder Hall bisa naik ke BH9
- Jam tidur/kerja, sesi helper, potongan event. Hero yang bentrok CWL **tidak dimasukkan ke saran & antrean**
  selama CWL — cukup disebut di ringkasan (Dashboard) & Tukang, lalu dilanjutkan setelah CWL selesai

**📊 Progres**
- Persentase menuju max TH/BH, perkiraan bulan selesai builder & lab, **hari kerja** builder & lab (helper ikut
  dihitung; Clock Tower di masa depan tidak diasumsikan), progres troop Star Lab sampai level max BH; syarat
  **Builder ke-6 (B.O.B)** di Builder Base
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
- Isinya sama dengan lembar **Desa anda**: Goblin Builder/Researcher tidak dihitung sebagai pekerja tetap
  (tampil sebagai 👺 di bawah nama desa), dan saran memakai urutan tercepat yang sama dengan Tukang
- Desa yang paling cepat bebas di atas; perbesar widget untuk menampilkan lebih banyak desa.
  Baris bawah: saran untuk pekerja yang bebas, atau upgrade berikutnya yang selesai
- Tablet: huruf lebih besar, baris mengisi tinggi widget, dan nama upgrade (+ jumlah sibuk, mis. 4/5)
  tampil di bawah tiap waktu;
  saat mendatar jumlah baris menyesuaikan tinggi widget (ukuran ditentukan dari layar penuh, jadi tetap benar
  walau Clash Timer sedang dibuka di layar terbagi)
- Hitung mundur < 24 jam memakai format Android sesuai bahasa HP: "17.03" = 17 menit 3 detik,
  "16.27.11" = 16 jam 27 menit 11 detik
- Ketuk baris → Dashboard desa itu; ketuk kotak Builder Base / Star Lab → Dashboard di Builder Base; ketuk judul
  "Semua desa" → lembar Desa anda. Diperbarui saat data berubah (import, Sudah dimulai, API), saat
  upgrade selesai, saat keluar dari aplikasi, dan paling lambat tiap 30 menit; mengikuti tema terang/gelap
- **↻ jam** di pojok kanan atas = terakhir diperbarui; ketuk untuk memperbarui sekarang
- Setiap pembaruan menggambar widget dari awal, jadi tampilan baru langsung terpakai setelah update aplikasi
  (sebelum pembaruan pertama widget hanya menampilkan "Memuat…", bukan data contoh)
- **Xiaomi / HyperOS**: Info aplikasi → aktifkan **Mulai otomatis** dan **Penghemat baterai → Tidak ada
  batasan**, supaya widget & notifikasi tetap diperbarui walau aplikasi ditutup (kartu petunjuk di Dashboard,
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
- Tab Progres → **Siapkan resource**: total Gold/Elixir/DE (dan Gold/Elixir BB) untuk upgrade yang dimulai
  dalam 24 jam / 3 hari / 7 hari (bar per sumber daya) + upgrade termahal

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

**⚙️ Opsi per desa** (ikon opsi di header, atau ketuk ringkasan strategi di Dashboard)
- Lembar **Opsi desa** (Home | Builder Base): kartu strategi menampilkan **tanggal target masing-masing**
  ("Semua upgrade TH11 selesai ±7 Feb", "Semua hero max ±7 Des"); Gold Pass & CWL bertanda **OTOMATIS**
  bila terdeteksi; sakelar collector; prioritas & disembunyikan. Semua disimpan **per desa**.
- Lembar ini juga berisi Update dari export, Ganti nama, Hapus desa, Potion & item, dan Pengaturan.
- **Gold Pass naik bertahap**: boost 10% terbuka di awal track, 15% dan 20% setelah poin tertentu (musim
  November 2026: Research 580/1.000, Builder 640/1.060 — bisa diubah di "Ambang"). Isi poin track sekarang
  di Opsi desa → Gold Pass: aplikasi memperkirakan tanggal tiap ambang tercapai (poin per hari dari riwayat
  poin, bawaan ±90, atau isi sendiri) dan rencana memakai persen yang naik mulai tanggal itu
- Jam tidur/kerja, event, ikon, nada & notifikasi (⚙️ Pengaturan) berlaku untuk semua desa.

**⚔️ Pengingat serangan** (butuh API key)
- CWL (hari perang, bila akun ada di lineup), war biasa (war log clan harus publik) dan Raid Weekend:
  3 jam sebelum berakhir aplikasi mengecek API dan memberi notifikasi hanya bila serangan masih tersisa.
  Ketuk notifikasi → membuka Clash of Clans.

**📌 Prioritaskan / sembunyikan** — tahan lama upgrade di Dashboard, Tukang, Lab, atau Progres
- Prioritaskan: didahulukan di saran, rencana, widget & notifikasi. Sembunyikan: hilang dari saran &
  rencana tetapi tetap dihitung di progres. Kelola di lembar **Opsi desa**.

**📈 Riwayat & statistik** (tab Progres)
- Grafik % menuju max per minggu, jam builder menganggur per minggu (Home / Builder Base) dan upgrade
  selesai per bulan — dikumpulkan dari setiap import.
- Statistik akun dari API: trophy & liga, war stars, donasi, Clan Capital, achievements, info clan,
  super troop aktif (juga di Dashboard).

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

**🖥️ Layar lebar** — di tablet atau HP landscape, Dashboard dan Tukang tampil berdampingan, navigasi di rel kiri.

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
  workflow) — bila workflow berubah, salin manual dari `.github/workflows/build.yml`.
- **Catatan rilis**: isi `app/src/main/assets/changelog.txt` (`# judul`, lalu `- butir` per perubahan) setiap
  build. Workflow menjadikannya isi rilis di GitHub (tampil di kartu update aplikasi), dan aplikasi
  menampilkannya sekali sebagai "Yang baru" setelah update. Bila file itu tidak berubah, isi rilis
  cukup "Perbaikan kecil".
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
