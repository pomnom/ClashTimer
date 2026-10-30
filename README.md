# Clash Timer

Aplikasi Android untuk memantau upgrade Clash of Clans dari **Export village data**,
dengan notifikasi yang tepat waktu (alarm sistem Android, bukan notifikasi browser).

## Fitur
- Import dari clipboard (atau "Bagikan" teks ke aplikasi) — Home Village & Builder Base
- Builder, Laboratory, Pet House, Star Lab, Builder Base, dan cooldown helper
- Hitung mundur langsung + jam selesai
- Notifikasi tepat waktu walau layar mati, dipasang ulang otomatis setelah HP restart
- Pengingat tambahan opsional (5–60 menit sebelum selesai)
- Banyak akun sekaligus (dibedakan dari tag pemain)

## Cara dapat APK (GitHub Actions)
1. Buat repository baru di GitHub (sebaiknya **Private**).
2. Upload seluruh isi folder ini ke repository tersebut (termasuk folder `.github`).
3. Buka tab **Actions** → tunggu "Build APK" selesai (±3–5 menit).
4. Unduh `ClashTimer.apk` dari halaman **Releases** (atau dari artifact di Actions).
5. Install di HP (izinkan "Install dari sumber tidak dikenal").

Setiap kali kode diubah dan di-push, APK baru otomatis dibuat dan bisa di-install
langsung di atas versi lama (kunci tanda tangan `app/clashtimer.keystore` tetap sama).

## Cara pakai
1. Di game: Settings → More Settings → paling bawah **Export village data** → Copy.
2. Buka Clash Timer → **Import dari Clipboard**.
3. Ulangi import setiap kali memulai upgrade baru.

## Catatan
- Timer = sisa waktu *saat export*. Kalau upgrade dipercepat (gem, book, potion)
  atau dibantu helper, import ulang supaya jadwal notifikasi ikut berubah.
- "Mode alarm kuat" (default aktif) memakai `setAlarmClock`, yang paling tahan
  terhadap Doze; efek sampingnya ada ikon jam kecil di status bar.
- Data nama bangunan berasal dari ClashKingAssets (via coc-rush-helper), Oktober 2026.

Fan content tidak resmi, tidak berafiliasi dengan Supercell.
