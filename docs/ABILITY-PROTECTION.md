# Ability protection: target-only

Secara default cast/right-click **tidak dibatalkan**. Target di claim tanpa build trust disaring
berdasarkan `mob-mode`, lalu damage/effect ability pada target tersebut ditolak.
Pemilik/build-trusted/bypass tetap mengikuti permission GP, termasuk subdivision.
Hit melee, sweeping dan bow biasa tidak dibatasi GPSafeGuard. Bila GP atau plugin
lain membatalkan hit, GPSafeGuard tidak membuka kembali pembatalannya.

## Mode mob

```yaml
ability-protection:
  enabled: true
  mob-mode: WHITELIST # ALL atau WHITELIST
  mob-whitelist:
    - minecraft:villager
    - minecraft:iron_golem
    - mythicmobs:PetDragon
  protect-players: false
  block-unattributed-skill-damage: true
  block-unattributed-plugin-effects: true
```

`WHITELIST` adalah daftar **mob yang dilindungi**, bukan mob yang boleh terkena.
Nama vanilla boleh memakai `minecraft:zombie` atau `ZOMBIE`. MythicMobs memakai
internal ID, bukan display name; matching tidak peka huruf besar/kecil. `ALL`
melindungi seluruh target non-player, termasuk armor stand. Dengan mode whitelist,
mob yang tidak terdaftar tidak diblokir layer ini. Wilderness tidak dibatasi.

Whitelist bawaan 1.21.11 berisi peliharaan/tunggangan/dekoratif (juga mob netral):
villager, wandering trader, allay, axolotl, bat, camel, cat, copper golem,
dolphin, donkey, fox, happy ghast, horse, llama, mule, nautilus, ocelot, parrot,
pufferfish, skeleton horse, strider, trader llama, tropical fish dan wolf.
Iron golem dikeluarkan karena umum dijadikan iron farm. Ternak/mob EXP dan mob
farm resource umum tidak masuk daftar ini. Villager dipertahankan untuk melindungi
trading hall. Ini preset praktis, bukan klasifikasi semua passive mob vanilla.
Config dengan dua entry default lama (villager + iron golem) otomatis diperbarui;
whitelist custom, termasuk daftar kosong, tetap dipertahankan. Pilih WHITELIST
untuk memakai daftar ini; mode ALL tetap melindungi semua target.

Config lama menambahkan `cast-guard.enabled: false` bila belum ada; setting eksplisit
tetap dipertahankan. Mode ALL ditambahkan bila belum ada.
Reload memakai `/gpsafeguard reload`. Ganti JAR dependency memerlukan restart.

## Opsi blokir cast

```yaml
ability-protection:
  cast-guard:
    enabled: true
    radius: 48
```

MythicLib PlayerCastSkillEvent (termasuk skill MMOItems) dibatalkan sebelum efek
diterapkan jika caster berada di claim tanpa build trust, ada target eksplisit
yang dilindungi tanpa izin, atau ada target dilindungi dalam radius pencarian
(kubus, radius 1–128). Pencarian target mengikuti ALL/WHITELIST. Buff/heal juga
dapat ditolak, wilderness dekat claim juga bisa terpengaruh. Hit biasa tidak
memanggil guard ini. `false` kembali ke target-only; proteksi damage tetap aktif.
Skill MythicMobs langsung yang tidak melewati PlayerCastSkillEvent tetap memakai
filter target/damage, bukan pembatalan cast global. Radius tidak menjamin cakupan
seluruh targeter custom/delayed. Periksa hook PlayerCastSkillEvent READY pada info.

Testing: true + reload, cast sebagai pemain untrusted dekat mob dilindungi harus
ditolak; owner/build-trusted diizinkan. false + reload, cast kembali berjalan dan
target tetap terlindungi. Uji WHITELIST dengan target yang masuk/tidak masuk daftar,
serta normal sword/bow untuk memastikan farm tetap dapat digunakan.

## Hook dan efek

- MythicLib PlayerAttackEvent: klasifikasi SKILL/ON_HIT/DOT/MINION. Serangan
  campuran membuang packet skill dan mempertahankan packet weapon. Event skill
  murni dibatalkan per target. Normal WEAPON/UNARMED/PROJECTILE tidak ditolak.
- OnHitEffectEvent: hanya efek tambahan dibatalkan, bukan normal hit sumbernya.
- MMOItems menggunakan pipeline target damage/on-hit MythicLib; tidak ada lagi
  pembatalan PlayerClickEvent atau item-in-hand right-click.
- MythicMobs damage/heal: pemeriksaan caster dan target, termasuk owner summon.
- Metadata target MythicMobs dari PlayerCastSkillEvent/MythicTriggerEvent:
  hapus target yang dilindungi dari daftar eksplisit, **tanpa cancel cast**.
- Bukkit fallback: CUSTOM/MAGIC atau event yang terklasifikasi sebagai skill;
  splash potion/cloud disaring per target. PLUGIN potion effect dan UNKNOWN
  knockback dapat diblokir karena API tersebut tidak membawa caster.

`block-unattributed-skill-damage` bisa ikut menolak skill mob liar atau skill owner
yang kehilangan caster. `block-unattributed-plugin-effects` bisa ikut menolak buff
plugin owner/admin karena Bukkit addPotionEffect/knockback tidak membawa caster.
Efek existing tidak dihapus; expiration/milk/death tidak dibatalkan. Damage dan
knockback normal tetap mengikuti GP, tanpa tebakan caster berdasarkan lokasi/waktu.

Hook opsional memakai reflection dan melaporkan READY/UNAVAILABLE di info/console.
READY hanya berarti API terdaftar, bukan verifikasi seluruh custom mechanic.
Targeter yang menghitung ulang target setelah filter, `setVelocity`, teleport,
`setHealth`/`remove` langsung, serta fire/setFireTicks yang tidak menyediakan event
atribusi tidak bisa dijamin oleh event standar ini. Uji mechanic server yang
dipakai; untuk kasus lolos diperlukan YAML skill dan versi API yang spesifik.

## Shelf

- Label default `1.21.9 Oak Shelf`. Nilai lama `1.21.11` dimigrasikan otomatis.
- Spawn dilakukan tick berikutnya setelah placement final tidak dibatalkan.
- Bedrock: Floodgate. Java <1.21.9: ViaVersion (protocol <773).
- Java memakai fallback handshake protocol Paper jika ViaVersion lokal tidak
  memberikan data. Bedrock juga terdeteksi lewat Geyser lokal tanpa Floodgate
  dan linked UUID Floodgate. Versi yang diperiksa adalah VIEWER, bukan placer.
- Label masuk normal tracking; PlayerTrackEntityEvent menolak viewer modern
  sebelum spawn packet. Refresh saat join/teleport/world change dan periodik
  menjaga label yang sudah ada. Tidak bergantung pada placer melihat label.
- Semua Bedrock terdeteksi mendapat label konservatif: API publik tidak memberi
  flag dukungan shelf mapping. Geyser/Floodgate proxy harus meneruskan data ke
  backend; backend yang hanya melihat protocol Java hasil translasi tidak dapat
  menebak versi asli. Pasang hook di backend bila log deteksi menunjukkan hal ini.
- `shelf-guard.tag-visible-to-all: true` untuk verifikasi tanpa hook deteksi.
- Debug menampilkan material block/item, blacklist match, cancellation, lokasi,
  UUID/validitas TextDisplay, teks dan jumlah viewer eligible.
- Label persistent yang sudah ada diperbarui saat reveal. Shelf yang belum punya
  label perlu di-break/place lagi; tidak ada global scan world.

## Testing manual

1. Jalankan `compile.bat`. Pasang JAR terbaru dari `builds/`, restart server,
   lalu `/gpsafeguard info` dan `/gpsafeguard debug`.
2. Shelf: sebagai owner place oak shelf di claim, periksa log Shelf place,
   TextDisplay spawned dan visibility. Lihat lewat Java lama dengan ViaVersion
   dan Bedrock Floodgate. Set tag-visible-to-all true + reload untuk memastikan
   entity benar-benar tampak dari client modern. Uji luar claim tanpa bypass:
   placement diblokir, display tidak dibuat. Uji break: display hilang.
   Uji dengan A (Java 1.21.11) yang memasang dan B (Java 1.21.8) yang hanya
   melihat: tag-visible-to-all false, A tidak melihat, B harus melihat.
   Ulangi B Bedrock, login setelah placement, teleport menjauh/mendekat,
   reload true -> false dan restart. Log Shelf viewer harus menunjukkan
   protocol B <773 atau Bedrock, eligible=true; Shelf tracking tidak cancelled
   dan serverCanSee=true. Itu memverifikasi deteksi/izin server; tampilan client
   tetap perlu diuji langsung. Client Java sebelum 1.19.4 perlu dukungan konversi
   TextDisplay dari ViaBackwards/translator, karena entity belum ada di client.
3. EXP farm: pemain non-trusted pukul zombie/skeleton dengan tangan, pedang biasa,
   pedang MMOItems tanpa proc, sweeping dan bow biasa. Plugin ini tidak boleh
   mengeluarkan pesan block normal hit. Jika masih cancelled, cek GP/plugin lain.
4. Aktifkan ALL: non-trusted cast direct skill, AoE, projectile, DoT, on-hit,
   debuff/heal dan knockback ke mob claim dari dalam/luar. Cast/visual tetap
   berjalan; target dilindungi tidak terkena damage/effect yang tertangani hook.
   Untuk on-hit, normal weapon damage masih ada tetapi extra skill ditolak.
5. Uji AoE lintas batas: mob wilderness terkena, mob claim tanpa izin tidak.
   Ulangi sebagai owner/build-trusted dan dua caster bersamaan.
6. WHITELIST: masukkan villager dan MythicMobs internal ID. Kedua target itu
   diproteksi dari ability, zombie yang tidak tercantum tetap terkena. Normal hit
   tetap tidak diblokir GPSafeGuard, termasuk terhadap whitelist target.
7. Uji subdivision, master toggle, reload, restart, trust invite dan shelf.
   Periksa health/efek/posisi sebelum-sesudah untuk tiap custom mechanic.

Jika ability lolos, sertakan versi GP/MMOItems/MythicLib/MythicMobs/ViaVersion,
YAML ability, ID mob, config, output info dan log debug. Testing runtime dilakukan
administrator; compile tidak membuktikan semua signature hook runtime cocok.

## Storage

Tidak ada storage permanen baru. Claim/trust dikelola GP; shelf memakai entity/PDC;
weak event caches dan cooldown memakai RAM. Bila ada kebutuhan data permanen baru,
gunakan SQLite/H2. YAML hanya untuk konfigurasi dan pesan.
