#!/usr/bin/env python3
"""
Menyusun app/src/main/assets/gamedata.json dari ClashKingAssets (data yang diekstrak dari file game)
ditambah tools/overrides.json (data yang tidak ada di ClashKing: kapasitas gudang, kamp Builder Base).

Pakai:
  python3 tools/build_gamedata.py                      # unduh data ClashKingAssets terbaru dari GitHub
  python3 tools/build_gamedata.py --src <folder/zip>   # pakai salinan lokal (folder atau zip repo)
  python3 tools/build_gamedata.py --check              # hanya cek: gagal bila hasil berbeda dari file sekarang
  python3 tools/build_gamedata.py --validate           # periksa gamedata.json yang ada saja (tanpa unduh; dipakai workflow)

Setelah menulis, skrip menjalankan pemeriksaan data (lihat validate()). Bila ada yang gagal, file tidak ditulis.
"""
import argparse, io, json, os, sys, urllib.request, zipfile
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "gamedata.json")
OVR = os.path.join(ROOT, "tools", "overrides.json")
CK_RAW = "https://raw.githubusercontent.com/ClashKingInc/ClashKingAssets/main/assets/"
FILES = ["buildings", "traps", "heroes", "troops", "spells", "pets", "equipment", "helpers"]


def clean(s):
    """Aturan nama ikon ClashKing (README ClashKingAssets)."""
    return s.lower().replace(" ", "_").replace(".", "").replace("?", "").replace("\\q", "").replace("’", "")


def load_src(src):
    """Kembalikan (static_data per file, set jalur ikon, tanggal data)."""
    if src and os.path.isdir(src):
        base = src
        for cand in ("assets", os.path.join("ClashKingAssets-main", "assets")):
            if os.path.isdir(os.path.join(src, cand)):
                base = os.path.join(src, cand)
        read = lambda p: open(os.path.join(base, p), encoding="utf-8").read()
        stamp = datetime.fromtimestamp(os.path.getmtime(os.path.join(base, "static_data", "buildings.json")), timezone.utc)
    else:
        if not src:
            # Cukup file JSON-nya (±3 MB), bukan seluruh repo berisi gambar.
            print("Mengunduh data ClashKingAssets …", file=sys.stderr)
            get = lambda p: urllib.request.urlopen(CK_RAW + p, timeout=120).read().decode("utf-8")
            data = {f: json.loads(get(f"static_data/{f}.json"))["items"] for f in FILES}
            man = json.loads(get("manifest.json"))
            paths = {a["path"] for group in man["assets"].values() for a in group}
            return data, paths, datetime.now(timezone.utc).strftime("%Y-%m-%d")
        z = zipfile.ZipFile(io.BytesIO(open(src, "rb").read()))
        prefix = next(n for n in z.namelist() if n.endswith("assets/static_data/buildings.json")).rsplit("static_data/", 1)[0]
        read = lambda p: z.read(prefix + p).decode("utf-8")
        stamp = datetime(*z.getinfo(prefix + "static_data/buildings.json").date_time, tzinfo=timezone.utc)
    data = {f: json.loads(read(f"static_data/{f}.json"))["items"] for f in FILES}
    man = json.loads(read("manifest.json"))
    paths = {a["path"] for group in man["assets"].values() for a in group}
    return data, paths, stamp.strftime("%Y-%m-%d")


def building_icon(paths, kind, village, name):
    folder = f"{kind}/{'builder-base' if village == 'builderBase' else 'home-village'}/{clean(name)}"
    levels = sorted(int(p[len(folder) + 7:-5]) for p in paths
                    if p.startswith(folder + "/level_") and p.endswith(".webp") and p[len(folder) + 7:-5].isdigit())
    return (folder, levels) if levels else (None, [])


def unit_icon(paths, kind, name):
    p = f"{kind}/{clean(name)}/icon.webp"
    return p if p in paths else None


REQ = {"troops": "required_lab_level", "spells": "required_lab_level",
       "heroes": "required_hero_tavern_level", "pets": "required_pet_house_level"}


def build(data, paths, ovr, version):
    items, names = [], {}
    cats = {"buildings": ("b", "B"), "traps": ("t", "T"), "heroes": ("h", "H"),
            "troops": ("u", "U"), "spells": ("s", "s"), "pets": ("p", "p")}
    weapons, wpn_items = {}, []
    # Urutan: semua Home (bangunan, jebakan, hero, troop, spell, pet), lalu Builder Base.
    passes = [(False, ["buildings", "traps", "heroes", "troops", "spells", "pets"]),
              (True, ["buildings", "heroes", "troops", "traps"])]
    for want_bb, files in passes:
      for f in files:
        for it in data[f]:
            bb = it.get("village") == "builderBase"
            if bb != want_bb:
                continue
            levels = it.get("levels") or []
            c = cats[f][1 if bb else 0]
            o = {"id": it["_id"], "n": it["name"], "c": c}
            if f in ("buildings",):
                o["t"] = it.get("type") or ""
            elif f == "traps":
                o["t"] = "Trap" if bb else ""
            else:
                o["t"] = ""
            o["r"] = it.get("upgrade_resource") or ""
            if f in REQ or f == "heroes":
                pb = it.get("production_building")
                o["pb"] = pb if pb is not None else (None if bb else "")
                o["pbl"] = it.get("production_building_level") or 0
            else:
                o["pb"], o["pbl"] = "", 0
            rows = []
            for l in levels:
                t = l.get("upgrade_time", l.get("build_time")) or 0
                cost = l.get("upgrade_cost", l.get("build_cost")) or 0
                hall = l.get("required_townhall") or 0
                req = l.get(REQ.get(f, ""), 0) or 0 if f in REQ else 0
                rows.append([l["level"], t, cost, hall, req])
            o["lv"] = rows
            mg = [[l["level"], [[m["_id"], m["level"], 1 if m.get("geared_up") else 0] for m in l["merge_requirement"]]]
                  for l in levels if l.get("merge_requirement")]
            if mg:
                o["mg"] = mg
            if f in ("buildings", "traps"):
                ic, il = building_icon(paths, f, it.get("village"), it["name"])
                if ic:
                    o["ic"], o["il"] = ic, il
            else:
                kind = {"troops": "troops", "heroes": "heroes", "pets": "pets"}.get(f)
                ic = unit_icon(paths, kind, it["name"]) if kind else (
                    f"spells/{clean(it['name'])}.webp" if f"spells/{clean(it['name'])}.webp" in paths else None)
                if ic:
                    o["ic"] = ic
            key = f"{'bb' if bb else 'home'}:{it['name']}"
            for k, v in ovr.get("items", {}).get(key, {}).items():
                o[k] = v
            items.append(o)
            names.setdefault(str(it["_id"]), it["name"])
            # Senjata Town Hall yang masih punya level (mulai TH17): item tersendiri, id 1000900 + TH.
            if f == "buildings" and it["_id"] == 1000001:
                for l in levels:
                    w = l.get("weapon")
                    if w and len(w.get("levels") or []) > 1:
                        wid = 1000900 + l["level"]
                        weapons[str(l["level"])] = wid
                        wpn_items.append({"id": wid, "n": w["name"], "c": "b", "t": "Weapon",
                                          "r": w.get("upgrade_resource") or "Gold", "pb": "", "pbl": 0,
                                          "lv": [[x["level"], x.get("build_time") or 0, x.get("build_cost") or 0, l["level"], 0]
                                                 for x in w["levels"]]})
    items += wpn_items
    for w in wpn_items:
        names.setdefault(str(w["id"]), w["n"])
    # Unlock bangunan per level Town Hall / Builder Hall
    by = {(it["_id"], it.get("village")): it for it in data["buildings"]}
    unlocks = {}
    for key, hid, vil in (("home", 1000001, "home"), ("bb", 1000034, "builderBase")):
        u = {}
        for l in by[(hid, vil)]["levels"]:
            lst = [[x["_id"], x["quantity"]] for x in (l.get("unlocks") or [])]
            if lst:
                u[str(l["level"])] = lst
        unlocks[key] = u
    # Pertahanan rakitan (Crafting Station) musim ini
    crafted = []
    cs = by.get((1000097, "home"))
    for d in (cs or {}).get("seasonal_defenses") or []:
        mods = []
        for m in d.get("modules") or []:
            short = m["name"][len(d["name"]):].strip() if m["name"].startswith(d["name"]) else m["name"]
            mods.append({"id": m["_id"], "n": m["name"], "s": short, "r": m.get("upgrade_resource") or "",
                         "lv": [[x["level"], x.get("build_time") or 0, x.get("build_cost") or 0, x.get("required_townhall") or 0]
                                for x in m.get("levels") or []]})
            names.setdefault(str(m["_id"]), f"{d['name']} · {short}")
        crafted.append({"id": d["_id"], "n": d["name"], "m": mods})
        names.setdefault(str(d["_id"]), d["name"])
    for e in data["equipment"]:
        names.setdefault(str(e["_id"]), e["name"])
    icons = {}
    for h in data["helpers"]:
        names.setdefault(str(h["_id"]), h["name"])
        p = f"helpers/{clean(h['name'])}.webp"
        if p in paths:
            icons[str(h["_id"])] = p
    max_th = max(l["level"] for l in by[(1000001, "home")]["levels"])
    max_bh = max(l["level"] for l in by[(1000034, "builderBase")]["levels"])
    return {"v": version, "maxTH": max_th, "maxBH": max_bh, "items": items, "unlocks": unlocks,
            "names": names, "icons": icons, "weapons": weapons, "crafted": crafted,
            "cap": ovr["capacity"]}


FROM_ROWS = set("ushpUH")


def validate(g):
    """Pemeriksaan data. Kembalikan daftar masalah (kosong = lolos)."""
    bad = []
    items = {it["id"]: it for it in g["items"] if it["c"] in "bthusp"}
    cap = g["cap"]
    at = lambda t, lv: 0 if lv <= 0 else t[min(lv, len(t)) - 1]
    def maxlv(iid, th):
        return max([r[0] for r in items[iid]["lv"] if r[3] <= th] or [0])
    def count(iid, th):
        return sum(q for k, v in g["unlocks"]["home"].items() if int(k) <= th for u, q in v if u == iid)
    def capacity(res, th):
        if res == "Dark Elixir":
            return at(cap["home"]["hall_dark"], th) + count(1000024, th) * at(cap["home"]["dark_storage"], maxlv(1000024, th))
        sid = 1000005 if res == "Gold" else 1000003
        return at(cap["home"]["hall"], th) + count(sid, th) * at(cap["home"]["storage"], maxlv(sid, th))
    h = cap["home"]
    if len(h["hall"]) < g["maxTH"] or len(h["hall_dark"]) < g["maxTH"]:
        bad.append(f"kapasitas Town Hall kurang dari TH{g['maxTH']}")
    for sid, key in ((1000005, "storage"), (1000024, "dark_storage")):
        top = max(r[0] for r in items[sid]["lv"])
        if len(h[key]) < top:
            bad.append(f"kapasitas {items[sid]['n']} hanya {len(h[key])} level, data game {top}")
    b = cap["bb"]
    if len(b["hall"]) < g["maxBH"]:
        bad.append(f"kapasitas Builder Hall kurang dari BH{g['maxBH']}")
    for th in range(7, g["maxTH"] + 1):
        for res in ("Gold", "Elixir", "Dark Elixir"):
            best = (0, "")
            for it in items.values():
                if it["r"] != res or it["n"] == "Wall":
                    continue
                rows = {r[0]: r for r in it["lv"]}
                for L, r in rows.items():
                    if r[3] > th:
                        continue
                    cst = (rows[L - 1][2] if (L - 1) in rows else 0) if it["c"] in FROM_ROWS else r[2]
                    if cst > best[0]:
                        best = (cst, f"{it['n']} Lv{L}")
            c = capacity(res, th)
            if best[0] > c:
                bad.append(f"TH{th}: {best[1]} butuh {best[0]:,} {res}, gudang max {c:,}")
    seen = set()
    for it in g["items"]:
        k = (it["id"], it["c"] in "BHUT")
        if k in seen:
            bad.append(f"id ganda {it['id']} {it['n']}")
        seen.add(k)
        lv = [r[0] for r in it["lv"]]
        if lv and lv != list(range(lv[0], lv[0] + len(lv))):
            bad.append(f"{it['n']}: level bolong {lv}")
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", help="folder atau zip ClashKingAssets (bawaan: unduh dari GitHub)")
    ap.add_argument("--check", action="store_true", help="jangan menulis; gagal bila hasil berbeda")
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--validate", action="store_true", help="periksa gamedata.json yang ada saja, tanpa mengunduh")
    a = ap.parse_args()
    if a.validate:
        g = json.load(open(a.out, encoding="utf-8"))
        bad = validate(g)
        if bad:
            print("❌ Pemeriksaan data gagal:", *bad, sep="\n  ", file=sys.stderr)
            sys.exit(1)
        print(f"✅ {os.path.relpath(a.out, ROOT)} lolos pemeriksaan ({len(g['items'])} item, data {g['v']})")
        return
    data, paths, version = load_src(a.src)
    ovr = json.load(open(OVR, encoding="utf-8"))
    g = build(data, paths, ovr, version)
    bad = validate(g)
    if bad:
        print("❌ Pemeriksaan data gagal:", *bad, sep="\n  ", file=sys.stderr)
        sys.exit(1)
    text = json.dumps(g, ensure_ascii=False, separators=(",", ":"))
    old = open(a.out, encoding="utf-8").read() if os.path.exists(a.out) else ""
    same = old and json.loads(old) | {"v": g["v"]} == g
    if a.check:
        print("✅ sama dengan file sekarang" if same else "⚠️ berbeda dari file sekarang")
        sys.exit(0 if same else 2)
    if same:
        print(f"✅ Data game tidak berubah ({g['v']}); file dibiarkan.")
        return
    open(a.out, "w", encoding="utf-8").write(text)
    print(f"✅ {a.out}: {len(g['items'])} item, TH{g['maxTH']}, BH{g['maxBH']}, data {g['v']}")


if __name__ == "__main__":
    main()
