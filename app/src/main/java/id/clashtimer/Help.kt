package id.clashtimer

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tutorial di balik tombol "?" — penjelasan dipindah dari layar ke sini supaya tampilan tetap bersih.
 * Semua teks bantuan ada di satu tempat.
 */
object Help {

    enum class Topic(val title: String, val body: String) {
        HOME(
            "Dashboard",
            "• Dashboard hanya untuk desa yang dipilih; ganti desa dengan mengetuk namanya di atas (lembar Desa anda).\n" +
                "• Paling atas: kartu hijau \"siap dipakai\" untuk Builder's Apprentice, Lab Assistant, Alchemist dan boost gratis Clock Tower begitu bisa dipakai. Ketuk setelah memakainya di game (helper: pilih upgrade yang dibantu) supaya cooldown & pengingatnya ikut.\n" +
                "• Kartu teratas = strategi & target, plus Gold Pass, event, CWL dan boost yang aktif. Ketuk untuk membuka Opsi desa.\n" +
                "• Dengan API key: kartu War / CWL menunjukkan serangan sudah dipakai atau belum. Setelah menyerang, upgrade hero yang selesai paling lambat 3 jam sebelum hari perang berikutnya berakhir ikut disarankan (tidak masuk lineup besok → batasnya lusa; hari terakhir CWL → hero bebas). Ketuk kartu untuk cek ulang API.\n" +
                "• Dua hitung mundur = tukang dan lab yang paling cepat selesai. Ketuk untuk membuka halaman Tukang / Lab.\n" +
                "• Pekerja bebas mendapat satu saran (B1… = builder, Lab, Pet, M = Master Builder). Saran teratas = urutan tercepat menuju target strategi.\n" +
                "• \"Sudah dimulai\" langsung memasang timer & notifikasi tanpa import ulang; salah pencet? ketuk Batalkan di pesan bawah.\n" +
                "• \"Opsi lain\" = semua pilihan dengan filter (selesai pas bangun, hero, pertahanan, murah).\n" +
                "• Hijau \"pas bangun\" = selesai saat Anda bisa langsung mengisinya lagi; oranye = pekerja akan menganggur.\n" +
                "• Ketuk upgrade yang berjalan → detail, tandai selesai (gem/book), potion, ubah sisa waktu (isi seperti di game), prioritas, atau batal. Tahan lama → prioritaskan / sembunyikan.\n" +
                "• Goblin Builder / Researcher (event) tidak dihitung sebagai pekerja tetap.",
        ),
        PLAN(
            "Tukang & Lab",
            "• Agenda (Tukang) = sesi login sampai Anda bangun lagi: kapan perlu membuka game dan apa yang dimulai. Pekerja yang bebas berdekatan (≤ 45 menit) digabung jadi satu sesi.\n" +
                "• Antrian = langkah berikutnya menurut rencana (Tukang: besok dan seterusnya, Lab: riset berikutnya).\n" +
                "• Rencana = perkiraan bila setiap pekerja langsung diisi saran teratas saat Anda online. Jam tidur/kerja, helper, Gold Pass, event, CWL & kapasitas gudang ikut dihitung.\n" +
                "• Target tiap strategi: Seimbang = semua upgrade di TH sekarang selesai · Rush TH = Town Hall naik · Hero dulu = semua hero max · Builder ke-6 = B.O.B terbuka · Max BB = Builder Base selesai. Ganti strategi di ikon opsi.\n" +
                "• Urutan prioritas rencana: (1) tercepat ke target strategi; bila sama cepat, (2) jeda menganggur terlama paling pendek, lalu (3) total jam menganggur paling sedikit — untuk semua pekerja, Home Village & Builder Base.\n" +
                "• Pekerja tidak dibiarkan menunggu pekerja lain: bila Lab / Star Lab tertahan gudang atau level lab, upgrade builder yang membukanya didahulukan.\n" +
                "• Semua hero dijalankan bersamaan. Seimbang: begitu builder kehabisan pekerjaan sementara lab masih jalan, Town Hall berikutnya dimulai lebih awal.\n" +
                "• Builder ke-6: syarat yang paling lama sisanya dikerjakan dulu (jalur kritis). Max BB di BH9 membuka B.O.B dulu karena Master Builder ke-4 mempercepat sisanya.\n" +
                "• Hero yang bentrok dengan CWL ditunda dan tidak dimasukkan ke antrean selama CWL.\n" +
                "• Ketuk sebuah langkah untuk detail; lonceng = pengingat untuk sesi itu. Saat lonceng sesi aktif, notifikasi \"selesai\" per upgrade di sesi itu masuk tanpa suara.",
        ),
        TIMELINE(
            "Timeline",
            "• Satu baris per pekerja (Tukang atau Lab) untuk 24 jam atau 3 hari.\n" +
                "• Batang penuh = sedang berjalan · transparan = rencana · garis merah = menganggur · abu-abu = jam tidur/offline · garis vertikal = sekarang.\n" +
                "• Emas = builder, ungu = lab, abu-abu = Goblin.\n" +
                "• Ketuk batang untuk detail.",
        ),
        NEEDS(
            "Kebutuhan sumber daya",
            "• Total biaya upgrade yang dimulai dalam 24 jam / 3 hari / 7 hari menurut rencana.\n" +
                "• Gold Pass & event sudah dihitung.\n" +
                "• Pastikan gudang cukup untuk upgrade termahal.",
        ),
        SIXTH(
            "Builder ke-6 (B.O.B)",
            "• Syarat: Builder Hall 9 + B.O.B Control, tiga gear-up, satu troop BB Lv18, satu pertahanan BB Lv9, Battle Machine + Battle Copter = 45.\n" +
                "• Bangunan & jebakan baru dipasang dulu supaya Builder Hall bisa naik.\n" +
                "• Gear-up dikerjakan Master Builder di Home Village (bayar Gold Home Village) dan butuh level bangunan di kedua desa.\n" +
                "• Level B.O.B Control diklaim gratis tiap syarat terpenuhi.\n" +
                "• Perkiraan tanpa Clock Tower boost, book & gem.",
        ),
        CLOCK(
            "Clock Tower",
            "• Boost mempercepat Master Builder & Star Lab 10× (bukan gear-up di Home Village).\n" +
                "• Sisa boost & cooldown dibaca langsung dari export; \"Pakai boost\" mencatat pemakaian sekarang.",
        ),
        ITEMS(
            "Potion & item",
            "• Item yang layak dipakai sekarang; ketuk untuk mencatat pemakaian.\n" +
                "• Boost yang aktif (termasuk yang terbaca dari export) sudah dihitung ke timer.",
        ),
        HELPERS(
            "Helper",
            "• Builder's Apprentice & Lab Assistant: tugaskan di game. Aplikasi memperkirakan sesinya tiap hari dan memotong timer upgrade yang paling lama.\n" +
                "• Ketuk helper yang sedang cooldown untuk mengubah timernya.",
        ),
        VILLAGES(
            "Desa anda",
            "• Ketuk sebuah desa untuk membukanya; ketuk kotak Tukang / Lab / BB / Star untuk langsung ke halamannya.\n" +
                "• Tahan lama sebuah desa → ganti nama, opsi, update, atau hapus.\n" +
                "• Login berikutnya = kapan tiap desa perlu dibuka lagi. Isinya sama dengan widget.\n" +
                "• Desa baru ditambah otomatis saat Anda meng-import export dari akun lain.",
        ),
        OPTIONS(
            "Opsi desa",
            "• Semua opsi di sini hanya untuk desa ini.\n" +
                "• Gold Pass naik bertahap: isi poin track sekarang di Gold Pass → boost 15% & 20% dipakai rencana mulai perkiraan tanggal tercapainya (dari poin per hari; perbarui poin kapan saja). Ambang poin bisa diubah tiap musim.\n" +
                "• Terdeteksi otomatis: jumlah builder, helper, B.O.B & gear-up (dari export), Gold Pass (dari lama timer saat import), CWL dan upgrade yang selesai lebih cepat (dari API, bila API key diisi).\n" +
                "• Strategi & collector sesuai pilihan Anda.",
        ),
        PROGRESS(
            "Progres",
            "• Persentase menuju max TH/BH dihitung dari waktu upgrade yang tersisa (bukan jumlah bangunan).\n" +
                "• Hari kerja = total lama upgrade yang tersisa (Gold Pass ikut), dikurangi bantuan Builder's Apprentice & Lab Assistant. Builder dibagi ke semua builder, jadi waktu nyatanya lebih pendek.\n" +
                "• Perkiraan tidak memakai boost Clock Tower berikutnya (hanya yang terbaca dari export).\n" +
                "• Tanggal selesai mengikuti rencana (urutan tercepat ke target, jam tidur, helper, Gold Pass).\n" +
                "• Ketuk sebuah kategori untuk melihat itemnya; tahan lama item untuk prioritaskan / sembunyikan.",
        ),
        SIMULATION(
            "Simulasi",
            "• Bandingkan tanggal selesai dengan/tanpa Gold Pass, Hammer Jam, tiga strategi saran, dan builder ke-6.\n" +
                "• Memakai jam tidur, helper, CWL & item yang diprioritaskan/disembunyikan; tanpa potion/book.\n" +
                "• Strategi lain ikut dicari urutan tercepatnya supaya perbandingannya adil.",
        ),
        CRAFTING(
            "Crafting Station",
            "• Level modul tiap pertahanan rakitan musim ini (dari export).\n" +
                "• Pertahanan rakitan berganti tiap fase Crafting; upgrade modul memakai builder.",
        ),
        EVENTS(
            "Event",
            "• Supercell tidak menyediakan jadwal event yang bisa dibaca aplikasi.\n" +
                "• Saat event muncul di game, aktifkan di sini dan cocokkan tanggalnya — saran, rencana dan timer langsung memakai potongannya.",
        ),
        NOTIFY(
            "Notifikasi",
            "• Senyap saat jam istirahat: upgrade yang selesai di jam tidur (dan jam kerja bila dipilih) masuk ke panel tanpa suara, getar, atau alarm; pengingat \"sebentar lagi\" dilewati. Saat jam istirahat selesai ada satu ringkasan bersuara (bisa dimatikan).\n" +
                "• Tes nada tetap berbunyi walau sedang jam istirahat.\n" +
                "• Mode alarm kuat memakai alarm sistem sehingga tidak ditunda saat HP tidur; efeknya ada ikon jam kecil di status bar.\n" +
                "• Alarm keras berbunyi berulang dengan volume alarm sampai notifikasi dibuka, diusap, atau ditekan Matikan.",
        ),
        WIDGET(
            "Widget & berjalan di latar",
            (if (Background.isXiaomi()) Background.TIP_BODY + "\n" else
                "• Bila widget atau notifikasi sering telat: izinkan aplikasi berjalan tanpa batasan baterai (dan Autostart bila HP punya).\n") +
                "• Widget diperbarui saat data berubah, saat upgrade selesai, dan paling lambat tiap 30 menit.\n" +
                "• Jam ↻ di widget = terakhir diperbarui; ketuk untuk memperbarui sekarang.\n" +
                "• Data tiap perangkat terpisah: update di HP tidak mengubah widget di tablet (pakai Pindah data).",
        ),
        BACKUP(
            "Pindah data & cadangan",
            "• Di perangkat lain: Pengaturan → Tempel data (atau Buka file). Data di perangkat tujuan diganti seluruhnya.\n" +
                "• Isinya termasuk API key — jangan bagikan teks/file ini ke orang lain.",
        ),
        UPDATE(
            "Update aplikasi",
            "• Ada build baru di GitHub → kartu \"Update\" muncul di layar utama.\n" +
                "• APK diunduh lewat pengunduh Android, lalu dipasang dari notifikasi \"Unduhan selesai\" (atau daftar Unduhan).\n" +
                "• File APK dihapus otomatis begitu build baru dibuka, atau setelah 3 hari bila tidak dipasang.\n" +
                "• Clash Timer sengaja tidak memasang APK sendiri supaya tidak ditandai Play Protect. Data tetap aman karena kunci tanda tangannya sama.",
        ),
    }

    /** Dialog tutorial; [extra] = keterangan tambahan khusus desa/saat ini (mis. urutan yang dipilih). */
    fun show(ctx: Context, topic: Topic, extra: String? = null) {
        val body = if (extra.isNullOrBlank()) topic.body else topic.body + "\n\n" + extra
        MaterialAlertDialogBuilder(ctx)
            .setTitle(topic.title)
            .setMessage(body)
            .setPositiveButton("Mengerti", null)
            .show()
    }

    /** Tombol "?" kecil yang membuka tutorial [topic]. */
    fun button(ctx: Context, topic: Topic, extra: (() -> String?)? = null): View =
        Ui.icon(ctx, R.drawable.ic_help, 16, Ui.secondary(ctx), "Bantuan: ${topic.title}") { show(ctx, topic, extra?.invoke()) }
}
