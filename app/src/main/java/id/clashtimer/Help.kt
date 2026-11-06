package id.clashtimer

import android.content.Context
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tutorial di balik tombol "?" — penjelasan dipindah dari layar ke sini supaya tampilan tetap bersih.
 * Semua teks bantuan ada di satu tempat.
 */
object Help {

    /** Judul & isi dihitung saat dibuka, supaya mengikuti bahasa yang sedang dipilih. */
    enum class Topic(private val titleOf: () -> String, private val bodyOf: () -> String) {
        HOME(
            { tr("Dashboard", "Dashboard") },
            { tr("• Dashboard hanya untuk desa yang dipilih; ganti desa dengan mengetuk namanya di atas (lembar Desa anda).\n", "• The Dashboard shows only the selected village; switch villages by tapping its name at the top (Your villages sheet).\n") +
                tr("• Paling atas: kartu hijau \"siap dipakai\" untuk Builder's Apprentice, Lab Assistant, Alchemist dan boost gratis Clock Tower begitu bisa dipakai. Ketuk setelah memakainya di game (helper: pilih upgrade yang dibantu) supaya cooldown & pengingatnya ikut.\n", "• At the very top: green \"ready to use\" cards for Builder\'s Apprentice, Lab Assistant, Alchemist and the free Clock Tower boost as soon as they can be used. Tap after using one in the game (helper: pick the upgrade it helps) so the cooldown & reminders follow.\n") +
                tr("• Kartu teratas = strategi & target, plus Gold Pass, event, CWL dan boost yang aktif. Ketuk untuk membuka Opsi desa.\n", "• Top card = strategy & target, plus active Gold Pass, events, CWL and boosts. Tap to open Village options.\n") +
                tr("• Kartu War / CWL (dari ClashKing, tanpa API key): war atau CWL yang sedang diikuti dan kapan hari perangnya berakhir — hanya info, rencana tidak menahan hero. Ketuk kartu untuk memperbarui.\n", "• War / CWL card (from ClashKing, no API key): the war or CWL you are in and when its war day ends — info only, the plan does not hold heroes. Tap the card to refresh.\n") +
                tr("• Dua hitung mundur = tukang dan lab yang paling cepat selesai. Ketuk untuk membuka halaman Tukang / Lab.\n", "• The two countdowns = the builder and lab finishing soonest. Tap to open the Builders / Lab page.\n") +
                tr("• Pekerja bebas mendapat satu saran (B1… = builder, Lab, Pet, M = Master Builder). Saran teratas = urutan tercepat menuju target strategi.\n", "• Each free worker gets one suggestion (B1… = builder, Lab, Pet, M = Master Builder). The top suggestion = fastest order toward the strategy target.\n") +
                tr("• \"Sudah dimulai\" langsung memasang timer & notifikasi tanpa import ulang; salah pencet? ketuk Batalkan di pesan bawah.\n", "• \"Started\" sets the timer & notifications right away without re-importing; tapped by mistake? tap Undo in the message at the bottom.\n") +
                tr("• \"Opsi lain\" = semua pilihan dengan filter (selesai pas bangun, hero, pertahanan, murah).\n", "• \"Other options\" = all choices with filters (done at wake-up, heroes, defenses, cheap).\n") +
                tr("• Hijau \"pas bangun\" = selesai saat Anda bisa langsung mengisinya lagi; oranye = pekerja akan menganggur.\n", "• Green \"at wake-up\" = finishes when you can refill it right away; orange = the worker will sit idle.\n") +
                tr("• Ketuk upgrade yang berjalan → detail, tandai selesai (gem/book), potion, ubah sisa waktu (isi seperti di game), prioritas, atau batal. Tahan lama → prioritaskan / sembunyikan.\n", "• Tap a running upgrade → details, mark done (gems/book), potion, edit remaining time (as shown in the game), prioritize, or cancel. Long-press → prioritize / hide.\n") +
                tr("• Goblin Builder / Researcher (event) tidak dihitung sebagai pekerja tetap.", "• Goblin Builder / Researcher (event) are not counted as regular workers.") },
        ),
        PLAN(
            { tr("Tukang & Lab", "Builders & Lab") },
            { tr("• Agenda (Tukang) = sesi login sampai Anda bangun lagi: kapan perlu membuka game dan apa yang dimulai. Pekerja yang bebas berdekatan (≤ 45 menit) digabung jadi satu sesi.\n", "• Agenda (Builders) = login sessions until you wake up again: when to open the game and what to start. Workers freeing up close together (≤ 45 minutes) are merged into one session.\n") +
                tr("• Antrian = langkah berikutnya menurut rencana (Tukang: besok dan seterusnya, Lab: riset berikutnya).\n", "• Queue = next steps according to the plan (Builders: tomorrow onward, Lab: next research).\n") +
                tr("• Rencana = perkiraan bila setiap pekerja langsung diisi saran teratas saat Anda online. Jam tidur/kerja, helper, Gold Pass, event & kapasitas gudang ikut dihitung.\n", "• Plan = estimate if every worker is refilled with the top suggestion while you are online. Sleep/work hours, helpers, Gold Pass, events & storage capacity are included.\n") +
                tr("• Target tiap strategi: Seimbang = semua upgrade di TH sekarang selesai · Rush TH = Town Hall naik · Hero dulu = semua hero max · Builder ke-6 = B.O.B terbuka · Max BB = Builder Base selesai. Ganti strategi di ikon opsi.\n", "• Target of each strategy: Balanced = all upgrades at the current TH done · Rush TH = Town Hall up · Heroes first = all heroes max · 6th Builder = B.O.B unlocked · Max BB = Builder Base done. Change the strategy with the options icon.\n") +
                tr("• Urutan prioritas rencana: (1) tercepat ke target strategi; bila sama cepat, (2) jeda menganggur terlama paling pendek, lalu (3) total jam menganggur paling sedikit — untuk semua pekerja, Home Village & Builder Base.\n", "• Plan priority: (1) fastest to the strategy target; if equally fast, (2) shortest longest idle gap, then (3) fewest total idle hours — for all workers, Home Village & Builder Base.\n") +
                tr("• Pekerja tidak dibiarkan menunggu pekerja lain: bila Lab / Star Lab tertahan gudang atau level lab, upgrade builder yang membukanya didahulukan.\n", "• Workers are not left waiting for other workers: if the Lab / Star Lab is held up by storage or lab level, the builder upgrade that unlocks it goes first.\n") +
                tr("• Semua hero dijalankan bersamaan. Seimbang: begitu builder kehabisan pekerjaan sementara lab masih jalan, Town Hall berikutnya dimulai lebih awal.\n", "• All heroes run at the same time. Balanced: once builders run out of work while the lab is still busy, the next Town Hall starts early.\n") +
                tr("• Builder ke-6: syarat yang paling lama sisanya dikerjakan dulu (jalur kritis). Star Laboratory dimulai tepat waktu (dihitung dengan lama menaikkan gudang & kapan Master Builder berikutnya bebas) supaya riset troop fokus tidak tertahan. Max BB di BH9 membuka B.O.B dulu karena Master Builder ke-4 mempercepat sisanya.\n", "• 6th Builder: the requirement with the most time left goes first (critical path). The Star Laboratory starts just in time (counting the time to raise storage & when the next Master Builder is free) so the focus troop research is not held up. Max BB at BH9 unlocks B.O.B first because the 4th Master Builder speeds up the rest.\n") +
                tr("• Ketuk sebuah langkah untuk detail; lonceng = pengingat untuk sesi itu. Saat lonceng sesi aktif, notifikasi \"selesai\" per upgrade di sesi itu masuk tanpa suara.", "• Tap a step for details; bell = reminder for that session. While a session bell is on, per-upgrade \"done\" notifications in that session arrive silently.") },
        ),
        TIMELINE(
            { tr("Timeline", "Timeline") },
            { tr("• Satu baris per pekerja (Tukang atau Lab) untuk 24 jam atau 3 hari.\n", "• One row per worker (Builders or Lab) for 24 hours or 3 days.\n") +
                tr("• Batang penuh = sedang berjalan · transparan = rencana · garis merah = menganggur · abu-abu = jam tidur/offline · garis vertikal = sekarang.\n", "• Solid bar = running · transparent = planned · red line = idle · grey = sleep/offline · vertical line = now.\n") +
                tr("• Emas = builder, ungu = lab, abu-abu = Goblin.\n", "• Gold = builder, purple = lab, grey = Goblin.\n") +
                tr("• Ketuk batang untuk detail.", "• Tap a bar for details.") },
        ),
        NEEDS(
            { tr("Kebutuhan sumber daya", "Resource needs") },
            { tr("• Total biaya upgrade yang dimulai dalam 24 jam / 3 hari / 7 hari menurut rencana.\n", "• Total cost of upgrades starting within 24 hours / 3 days / 7 days according to the plan.\n") +
                tr("• Gold Pass & event sudah dihitung.\n", "• Gold Pass & events are included.\n") +
                tr("• Pastikan gudang cukup untuk upgrade termahal.", "• Make sure your storage fits the most expensive upgrade.") },
        ),
        SIXTH(
            { tr("Builder ke-6 (B.O.B)", "6th Builder (B.O.B)") },
            { tr("• Syarat: Builder Hall 9 + B.O.B Control, tiga gear-up, satu troop BB Lv18, satu pertahanan BB Lv9, Battle Machine + Battle Copter = 45.\n", "• Requirements: Builder Hall 9 + B.O.B Control, three gear-ups, one BB troop at Lv18, one BB defense at Lv9, Battle Machine + Battle Copter = 45.\n") +
                tr("• Bangunan & jebakan baru dipasang dulu supaya Builder Hall bisa naik.\n", "• New buildings & traps are placed first so the Builder Hall can be upgraded.\n") +
                tr("• Gear-up dikerjakan Master Builder di Home Village (bayar Gold Home Village) dan butuh level bangunan di kedua desa.\n", "• Gear-ups are done by the Master Builder in the Home Village (paid with Home Village Gold) and need building levels in both villages.\n") +
                tr("• Level B.O.B Control diklaim gratis tiap syarat terpenuhi.\n", "• B.O.B Control levels are claimed for free as each requirement is met.\n") +
                tr("• Perkiraan tanpa Clock Tower boost, book & gem.", "• Estimate without Clock Tower boosts, books & gems.") },
        ),
        CLOCK(
            { tr("Clock Tower", "Clock Tower") },
            { tr("• Boost mempercepat Master Builder & Star Lab 10× (bukan gear-up di Home Village).\n", "• The boost speeds up the Master Builder & Star Lab 10× (not gear-ups in the Home Village).\n") +
                tr("• Sisa boost & cooldown dibaca langsung dari export; \"Pakai boost\" mencatat pemakaian sekarang.", "• Remaining boost & cooldown are read directly from the export; \"Use boost\" records using it now.") },
        ),
        ITEMS(
            { tr("Potion & item", "Potions & items") },
            { tr("• Item yang layak dipakai sekarang; ketuk untuk mencatat pemakaian.\n", "• Items worth using now; tap to record using one.\n") +
                tr("• Boost yang aktif (termasuk yang terbaca dari export) sudah dihitung ke timer.", "• Active boosts (including those read from the export) are already counted in the timers.") },
        ),
        HELPERS(
            { tr("Helper", "Helpers") },
            { tr("• Builder's Apprentice & Lab Assistant: tugaskan di game. Aplikasi memperkirakan sesinya tiap hari dan memotong timer upgrade yang paling lama.\n", "• Builder\'s Apprentice & Lab Assistant: assign them in the game. The app estimates their session each day and takes it off the longest upgrade timer.\n") +
                tr("• Ketuk helper yang sedang cooldown untuk mengubah timernya.", "• Tap a helper on cooldown to change its timer.") },
        ),
        VILLAGES(
            { tr("Desa anda", "Your villages") },
            { tr("• Ketuk sebuah desa untuk membukanya di Dashboard; untuk desa yang sedang dibuka, kotak Tukang / Lab / BB / Star langsung ke halamannya.\n", "• Tap a village to open it on the Dashboard; for the village already open, the Builders / Lab / BB / Star tiles go straight to that page.\n") +
                tr("• Tahan lama sebuah desa → ganti nama, opsi, update, atau hapus.\n", "• Long-press a village → rename, options, update, or delete.\n") +
                tr("• Login berikutnya = kapan tiap desa perlu dibuka lagi. Isinya sama dengan widget.\n", "• Next login = when each village needs to be opened again. Same as the widget.\n") +
                tr("• Desa baru ditambah otomatis saat Anda meng-import export dari akun lain.", "• New villages are added automatically when you import an export from another account.") },
        ),
        OPTIONS(
            { tr("Opsi desa", "Village options") },
            { tr("• Semua opsi di sini hanya untuk desa ini.\n", "• All options here apply to this village only.\n") +
                tr("• Gold Pass naik bertahap: isi poin track sekarang di Gold Pass → boost 15% & 20% dipakai rencana mulai perkiraan tanggal tercapainya (dari poin per hari; perbarui poin kapan saja). Ambang poin bisa diubah tiap musim.\n", "• Gold Pass rises in steps: enter your current track points under Gold Pass → the plan uses the 15% & 20% boosts from the estimated date you reach them (from points per day; update the points any time). The point thresholds can be changed each season.\n") +
                tr("• Terdeteksi otomatis: jumlah builder, helper, B.O.B & gear-up (dari export), Gold Pass (dari lama timer saat import).\n", "• Detected automatically: number of builders, helpers, B.O.B & gear-ups (from the export), Gold Pass (from timer lengths at import).\n") +
                tr("• Strategi & collector sesuai pilihan Anda.", "• Strategy & collectors as you choose.") },
        ),
        PROGRESS(
            { tr("Progres", "Progress") },
            { tr("• Persentase menuju max TH/BH dihitung dari waktu upgrade yang tersisa (bukan jumlah bangunan).\n", "• Percentage toward max TH/BH is based on the upgrade time left (not the number of buildings).\n") +
                tr("• Hari kerja = total lama upgrade yang tersisa (Gold Pass ikut), dikurangi bantuan Builder's Apprentice & Lab Assistant. Builder dibagi ke semua builder, jadi waktu nyatanya lebih pendek.\n", "• Work days = total upgrade time left (with Gold Pass), minus help from Builder\'s Apprentice & Lab Assistant. Builder time is spread over all builders, so the real time is shorter.\n") +
                tr("• Perkiraan tidak memakai boost Clock Tower berikutnya (hanya yang terbaca dari export).\n", "• The estimate does not use future Clock Tower boosts (only those read from the export).\n") +
                tr("• Tanggal selesai mengikuti rencana (urutan tercepat ke target, jam tidur, helper, Gold Pass).\n", "• Finish dates follow the plan (fastest order to the target, sleep hours, helpers, Gold Pass).\n") +
                tr("• Ketuk sebuah kategori untuk melihat itemnya; tahan lama item untuk prioritaskan / sembunyikan.", "• Tap a category to see its items; long-press an item to prioritize / hide.") },
        ),
        SIMULATION(
            { tr("Simulasi", "Simulation") },
            { tr("• Bandingkan tanggal selesai dengan/tanpa Gold Pass, Hammer Jam, tiga strategi saran, dan builder ke-6.\n", "• Compare finish dates with/without Gold Pass, Hammer Jam, the three suggestion strategies, and the 6th builder.\n") +
                tr("• Memakai jam tidur, helper & item yang diprioritaskan/disembunyikan; tanpa potion/book.\n", "• Uses sleep hours, helpers & prioritized/hidden items; without potions/books.\n") +
                tr("• Strategi lain ikut dicari urutan tercepatnya supaya perbandingannya adil.", "• Other strategies also get their fastest order so the comparison is fair.") },
        ),
        CRAFTING(
            { tr("Crafting Station", "Crafting Station") },
            { tr("• Level modul tiap pertahanan rakitan musim ini (dari export).\n", "• Module levels of each crafted defense this season (from the export).\n") +
                tr("• Pertahanan rakitan berganti tiap fase Crafting; upgrade modul memakai builder.", "• Crafted defenses change every Crafting phase; module upgrades use a builder.") },
        ),
        EVENTS(
            { tr("Event", "Events") },
            { tr("• Supercell tidak menyediakan jadwal event yang bisa dibaca aplikasi.\n", "• Supercell does not provide an event schedule that apps can read.\n") +
                tr("• Saat event muncul di game, aktifkan di sini dan cocokkan tanggalnya — saran, rencana dan timer langsung memakai potongannya.", "• When an event appears in the game, enable it here and match its dates — suggestions, the plan and timers use its discount right away.") },
        ),
        NOTIFY(
            { tr("Notifikasi", "Notifications") },
            { tr("• Senyap saat jam istirahat: upgrade yang selesai di jam tidur (dan jam kerja bila dipilih) masuk ke panel tanpa suara, getar, atau alarm; pengingat \"sebentar lagi\" dilewati. Saat jam istirahat selesai ada satu ringkasan bersuara (bisa dimatikan).\n", "• Silent during rest hours: upgrades finishing during sleep (and work hours if chosen) go to the panel without sound, vibration or alarm; \"almost done\" reminders are skipped. When rest hours end there is one summary with sound (can be turned off).\n") +
                tr("• Tes nada tetap berbunyi walau sedang jam istirahat.\n", "• The tone test still plays during rest hours.\n") +
                tr("• Mode alarm kuat memakai alarm sistem sehingga tidak ditunda saat HP tidur; efeknya ada ikon jam kecil di status bar.\n", "• Strong alarm mode uses the system alarm so it is not delayed while the phone sleeps; as a side effect a small clock icon shows in the status bar.\n") +
                tr("• Alarm keras berbunyi berulang dengan volume alarm sampai notifikasi dibuka, diusap, atau ditekan Matikan.", "• Loud alarms repeat at alarm volume until the notification is opened, swiped, or Stop is pressed.") },
        ),
        WIDGET(
            { tr("Widget & berjalan di latar", "Widget & background") },
            { (if (Background.isXiaomi()) Background.TIP_BODY + "\n" else
                tr("• Bila widget atau notifikasi sering telat: izinkan aplikasi berjalan tanpa batasan baterai (dan Autostart bila HP punya).\n", "• If widgets or notifications are often late: allow the app to run without battery restrictions (and Autostart if your phone has it).\n")) +
                tr("• Widget diperbarui saat data berubah, saat upgrade selesai, dan paling lambat tiap 30 menit.\n", "• The widget updates when data changes, when an upgrade finishes, and at least every 30 minutes.\n") +
                tr("• Jam ↻ di widget = terakhir diperbarui; ketuk untuk memperbarui sekarang.\n", "• The ↻ time on the widget = last updated; tap to update now.\n") +
                tr("• Data tiap perangkat terpisah: update di HP tidak mengubah widget di tablet (pakai Pindah data).", "• Data is separate per device: updating on your phone does not change the widget on a tablet (use Move data).") },
        ),
        BACKUP(
            { tr("Pindah data & cadangan", "Move data & backup") },
            { tr("• Di perangkat lain: Pengaturan → Tempel data (atau Buka file). Data di perangkat tujuan diganti seluruhnya.\n", "• On the other device: Settings → Paste data (or Open file). Data on the target device is replaced completely.\n") +
                tr("• Data ini tidak berisi API key, tetapi tetap berisi semua desa & riwayat Anda.", "• This data contains no API key, but it does contain all your villages & history.") },
        ),
        UPDATE(
            { tr("Update aplikasi", "App updates") },
            { tr("• Ada build baru di GitHub → kartu \"Update\" muncul di layar utama.\n", "• A new build on GitHub → an \"Update\" card appears on the main screen.\n") +
                tr("• APK diunduh lewat pengunduh Android, lalu dipasang dari notifikasi \"Unduhan selesai\" (atau daftar Unduhan).\n", "• The APK is downloaded with Android\'s downloader, then installed from the \"Download complete\" notification (or the Downloads list).\n") +
                tr("• File APK dihapus otomatis begitu build baru dibuka, atau setelah 3 hari bila tidak dipasang.\n", "• The APK file is deleted automatically once the new build is opened, or after 3 days if not installed.\n") +
                tr("• Clash Timer sengaja tidak memasang APK sendiri supaya tidak ditandai Play Protect. Data tetap aman karena kunci tanda tangannya sama.", "• Clash Timer deliberately does not install the APK itself so Play Protect does not flag it. Your data stays safe because the signing key is the same.") },
        ),
        ;

        val title: String get() = titleOf()
        val body: String get() = bodyOf()
    }

    /** Dialog tutorial; [extra] = keterangan tambahan khusus desa/saat ini (mis. urutan yang dipilih). */
    fun show(ctx: Context, topic: Topic, extra: String? = null) {
        val body = if (extra.isNullOrBlank()) topic.body else topic.body + "\n\n" + extra
        MaterialAlertDialogBuilder(ctx)
            .setTitle(topic.title)
            .setMessage(body)
            .setPositiveButton(tr("Mengerti", "Got it"), null)
            .show()
    }

    /** Tombol "?" kecil yang membuka tutorial [topic]. */
    fun button(ctx: Context, topic: Topic, extra: (() -> String?)? = null): View =
        Ui.icon(ctx, R.drawable.ic_help, 16, Ui.secondary(ctx), tr("Bantuan: ${topic.title}", "Help: ${topic.title}")) { show(ctx, topic, extra?.invoke()) }
}
