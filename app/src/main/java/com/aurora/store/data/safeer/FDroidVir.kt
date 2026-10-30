package com.aurora.store.data.safeer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.jar.JarFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * F-Droid kot drugi vir aplikacij v Safeer Play Store (odprte aplikacije brez Googla).
 *
 * Varnost po vzoru odjemalca F-Droid:
 *  - entry.jar je podpisan s kljucem repozitorija F-Droid; podpis preverimo proti pripetemu odtisu [FDROID_KLJUC].
 *  - entry.json pove SHA-256 kazala index-v2.json; kazalo preberemo sproti (brez nalaganja 70 MB v pomnilnik)
 *    in ga sprejmemo le, ce se vsota ujema.
 *  - Paket pred namestitvijo preverimo: SHA-256 datoteke iz kazala, ime paketa in podpisnik iz kazala.
 *  - Kazala ne prenesemo znova, dokler se casovni zig v entry.json ne spremeni.
 */
object FDroidVir {

    private const val TAG = "SafeerFDroid"
    const val REPO = "https://f-droid.org/repo"

    /** SHA-256 potrdila, s katerim F-Droid podpisuje repozitorij (entry.jar). */
    const val FDROID_KLJUC = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"

    private const val KATALOG = "fdroid-katalog.json"

    data class Aplikacija(
        val paket: String,
        val ime: String,
        val opis: String,
        val zvrsti: List<String>,
        val ikona: String?,
        val posodobljeno: Long,
        val verzija: String,
        val verzijaKoda: Long,
        val datoteka: String,
        val sha256: String,
        val velikost: Long,
        val podpisniki: List<String>
    )

    data class Katalog(
        val zig: Long,
        val zvrsti: Map<String, String>,
        val aplikacije: List<Aplikacija>
    )

    @Volatile
    private var vPomnilniku: Katalog? = null

    private fun povezava(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Safeer-Play-Store")
        }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** Shranjen katalog (brez omrezja); null, ce ga se ni. */
    fun shranjen(context: Context): Katalog? {
        vPomnilniku?.let { return it }
        val f = File(context.filesDir, KATALOG)
        if (!f.isFile) return null
        return try {
            izJson(JSONObject(f.readText())).also { vPomnilniku = it }
        } catch (e: Exception) {
            Log.w(TAG, "Shranjen katalog ni berljiv: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Osvezi katalog z F-Droida, ce je na voljo novejsi. Vrne veljaven katalog (nov ali shranjen).
     * [napredek] dobi odstotek prenosa kazala.
     */
    fun osvezi(context: Context, napredek: (Int) -> Unit = {}): Katalog {
        val vnos = preberiVnos(context)
        val zig = vnos.optLong("timestamp")
        shranjen(context)?.let { if (it.zig == zig) return it }
        val kazalo = vnos.getJSONObject("index")
        val pricakovana = kazalo.getString("sha256").lowercase()
        val velikost = kazalo.optLong("size")
        val novi =
            preberiKazalo(REPO + kazalo.getString("name"), pricakovana, velikost, zig, napredek)
        File(context.filesDir, KATALOG).writeText(vJson(novi).toString())
        vPomnilniku = novi
        return novi
    }

    /** entry.jar: preverjen podpis F-Droida, vrne entry.json. */
    private fun preberiVnos(context: Context): JSONObject {
        val f = File(context.cacheDir, "fdroid-entry.jar")
        val c = povezava("$REPO/entry.jar")
        try {
            if (c.responseCode != 200) throw IOException("F-Droid ${c.responseCode}")
            c.inputStream.use { v -> f.outputStream().use { v.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        JarFile(f, true).use { jar ->
            val vnos = jar.getJarEntry("entry.json") ?: throw SecurityException("entry.json manjka")
            // Podpis JAR se preveri sele, ko je vnos prebran do konca.
            val besedilo = jar.getInputStream(vnos).use { it.readBytes() }.toString(Charsets.UTF_8)
            val podpisniki = vnos.codeSigners ?: throw SecurityException("entry.json ni podpisan")
            val sha = MessageDigest.getInstance("SHA-256")
            val ok = podpisniki.any { p ->
                p.signerCertPath.certificates.any {
                    hex(sha.digest(it.encoded)) ==
                        FDROID_KLJUC
                }
            }
            if (!ok) throw SecurityException("entry.jar ni podpisan s kljucem F-Droid")
            return JSONObject(besedilo)
        }
    }

    private fun lokaliziran(r: JsonReader): String {
        val prevodi = HashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            val jezik = r.nextName()
            if (r.peek() == JsonToken.STRING) prevodi[jezik] = r.nextString() else r.skipValue()
        }
        r.endObject()
        return prevodi["sl"] ?: prevodi["en-US"] ?: prevodi["en"]
            ?: prevodi.values.firstOrNull().orEmpty()
    }

    private fun seznamNizov(r: JsonReader): List<String> {
        val izid = mutableListOf<String>()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() ==
                JsonToken.STRING
            ) {
                izid += r.nextString()
            } else {
                r.skipValue()
            }
        }
        r.endArray()
        return izid
    }

    private fun preberiKazalo(
        url: String,
        pricakovana: String,
        velikost: Long,
        zig: Long,
        napredek: (Int) -> Unit
    ): Katalog {
        val md = MessageDigest.getInstance("SHA-256")
        val sdk = Build.VERSION.SDK_INT
        val abi = Build.SUPPORTED_ABIS.toSet()
        val zvrsti = LinkedHashMap<String, String>()
        val aplikacije = ArrayList<Aplikacija>(5000)
        val c = povezava(url)
        try {
            if (c.responseCode != 200) throw IOException("F-Droid kazalo ${c.responseCode}")
            var prebrano = 0L
            var zadnji = -1
            val stevec = object : java.io.FilterInputStream(c.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = super.read(b, off, len)
                    if (n > 0 && velikost > 0) {
                        prebrano += n
                        val o = (prebrano * 100 / velikost).toInt()
                        if (o != zadnji) {
                            zadnji = o
                            napredek(o)
                        }
                    }
                    return n
                }
            }
            val vhod = DigestInputStream(stevec.buffered(256 * 1024), md)
            JsonReader(InputStreamReader(vhod, Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "repo" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                if (r.nextName() == "categories") {
                                    r.beginObject()
                                    while (r.hasNext()) {
                                        val id = r.nextName()
                                        var ime = id
                                        r.beginObject()
                                        while (r.hasNext()) {
                                            if (r.nextName() ==
                                                "name"
                                            ) {
                                                ime = lokaliziran(r)
                                            } else {
                                                r.skipValue()
                                            }
                                        }
                                        r.endObject()
                                        zvrsti[id] = ime
                                    }
                                    r.endObject()
                                } else {
                                    r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        "packages" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val paket = r.nextName()
                                preberiPaket(r, paket, sdk, abi)?.let { aplikacije += it }
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                // Ostanek toka do konca, da je vsota celotne datoteke.
                val ostanek = ByteArray(64 * 1024)
                while (vhod.read(ostanek) >= 0) { /* do konca */ }
            }
        } finally {
            c.disconnect()
        }
        val vsota = hex(md.digest())
        if (vsota !=
            pricakovana
        ) {
            throw SecurityException("kazalo F-Droid se ne ujema s podpisanim vnosom")
        }
        aplikacije.sortByDescending { it.posodobljeno }
        Log.i(
            TAG,
            "Katalog F-Droid: ${aplikacije.size} aplikacij za to napravo, ${zvrsti.size} zvrsti"
        )
        return Katalog(zig, zvrsti, aplikacije)
    }

    private class Razlicica(
        val verzija: String,
        val koda: Long,
        val datoteka: String,
        val sha256: String,
        val velikost: Long,
        val podpisniki: List<String>
    )

    private fun preberiPaket(
        r: JsonReader,
        paket: String,
        sdk: Int,
        abi: Set<String>
    ): Aplikacija? {
        var ime = paket
        var opis = ""
        var zvrsti = emptyList<String>()
        var ikona: String? = null
        var posodobljeno = 0L
        var najboljsa: Razlicica? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "metadata" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> ime = lokaliziran(r)
                            "summary" -> opis = lokaliziran(r)
                            "categories" -> zvrsti = seznamNizov(r)
                            "lastUpdated" -> posodobljeno = r.nextLong()
                            "icon" -> {
                                val poti = HashMap<String, String>()
                                r.beginObject()
                                while (r.hasNext()) {
                                    val jezik = r.nextName()
                                    r.beginObject()
                                    while (r.hasNext()) {
                                        if (r.nextName() ==
                                            "name"
                                        ) {
                                            poti[jezik] = r.nextString()
                                        } else {
                                            r.skipValue()
                                        }
                                    }
                                    r.endObject()
                                }
                                r.endObject()
                                ikona = poti["en-US"] ?: poti.values.firstOrNull()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "versions" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        r.nextName()
                        val v = preberiRazlicico(r, sdk, abi) ?: continue
                        if (najboljsa == null || v.koda > najboljsa!!.koda) najboljsa = v
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        val v = najboljsa ?: return null
        return Aplikacija(
            paket, ime, opis, zvrsti, ikona, posodobljeno,
            v.verzija, v.koda, v.datoteka, v.sha256, v.velikost, v.podpisniki
        )
    }

    /** Ena razlicica; null, ce ni za to napravo (premalo nov Android, druga arhitektura) ali ji kaj manjka. */
    private fun preberiRazlicico(r: JsonReader, sdk: Int, abi: Set<String>): Razlicica? {
        var datoteka = ""
        var sha = ""
        var velikost = 0L
        var verzija = ""
        var koda = 0L
        var minSdk = 1
        var podpisniki = emptyList<String>()
        var nativna = emptyList<String>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "file" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> datoteka = r.nextString()
                            "sha256" -> sha = r.nextString().lowercase()
                            "size" -> velikost = r.nextLong()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "manifest" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "versionName" -> verzija = r.nextString()
                            "versionCode" -> koda = r.nextLong()
                            "nativecode" -> nativna = seznamNizov(r)
                            "usesSdk" -> {
                                r.beginObject()
                                while (r.hasNext()) {
                                    if (r.nextName() ==
                                        "minSdkVersion"
                                    ) {
                                        minSdk = r.nextInt()
                                    } else {
                                        r.skipValue()
                                    }
                                }
                                r.endObject()
                            }
                            "signer" -> {
                                r.beginObject()
                                while (r.hasNext()) {
                                    if (r.nextName() ==
                                        "sha256"
                                    ) {
                                        podpisniki = seznamNizov(r).map { it.lowercase() }
                                    } else {
                                        r.skipValue()
                                    }
                                }
                                r.endObject()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (datoteka.isEmpty() || sha.isEmpty() || podpisniki.isEmpty()) return null
        if (minSdk > sdk) return null
        if (nativna.isNotEmpty() && nativna.none { it in abi }) return null
        return Razlicica(verzija, koda, datoteka, sha, velikost, podpisniki)
    }

    fun ikonaUrl(a: Aplikacija): String? = a.ikona?.let { REPO + it }

    /** Prenese paket, preveri SHA-256 iz kazala, ime paketa in podpisnika iz kazala. */
    fun prenesiInPreveri(context: Context, a: Aplikacija, napredek: (Int) -> Unit): File {
        val mapa = File(context.cacheDir, "fdroid-apk").apply { mkdirs() }
        mapa.listFiles()?.forEach { it.delete() }
        val cilj = File(mapa, "${a.paket}-${a.verzijaKoda}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val c = povezava(REPO + a.datoteka)
        try {
            if (c.responseCode != 200) throw IOException("Prenos ${c.responseCode}")
            var preneseno = 0L
            var zadnji = -1
            c.inputStream.use { vhod ->
                cilj.outputStream().use { izhod ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = vhod.read(buf)
                        if (n < 0) break
                        izhod.write(buf, 0, n)
                        md.update(buf, 0, n)
                        preneseno += n
                        if (a.velikost > 0) {
                            val o = (preneseno * 100 / a.velikost).toInt()
                            if (o != zadnji) {
                                zadnji = o
                                napredek(o)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (hex(md.digest()) != a.sha256) {
            cilj.delete()
            throw SecurityException("kontrolna vsota se ne ujema s kazalom F-Droid")
        }
        try {
            preveriPodpis(context, cilj, a)
        } catch (e: SecurityException) {
            cilj.delete()
            throw e
        }
        return cilj
    }

    @Suppress("DEPRECATION")
    private fun preveriPodpis(context: Context, f: File, a: Aplikacija) {
        val zastavice = PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val info: PackageInfo = context.packageManager.getPackageArchiveInfo(f.path, zastavice)
            ?: throw SecurityException("datoteka ni veljaven paket")
        if (info.packageName !=
            a.paket
        ) {
            throw SecurityException("napačen paket ${info.packageName}")
        }
        val podpisi = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.let { podpisi += it }
        info.signatures?.let { podpisi += it }
        val sha = MessageDigest.getInstance("SHA-256")
        if (podpisi.none { hex(sha.digest(it.toByteArray())) in a.podpisniki }) {
            throw SecurityException("podpisnik ni tisti iz kazala F-Droid")
        }
    }

    private fun vJson(k: Katalog): JSONObject {
        val apl = JSONArray()
        for (a in k.aplikacije) {
            apl.put(
                JSONObject()
                    .put("p", a.paket).put("i", a.ime).put("o", a.opis)
                    .put("z", JSONArray(a.zvrsti)).put("k", a.ikona ?: "")
                    .put("t", a.posodobljeno).put("v", a.verzija).put("c", a.verzijaKoda)
                    .put("d", a.datoteka).put("s", a.sha256).put("b", a.velikost)
                    .put("g", JSONArray(a.podpisniki))
            )
        }
        val zv = JSONObject()
        k.zvrsti.forEach { (id, ime) -> zv.put(id, ime) }
        return JSONObject().put("zig", k.zig).put("zvrsti", zv).put("aplikacije", apl)
    }

    private fun niz(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }

    private fun izJson(o: JSONObject): Katalog {
        val zv = LinkedHashMap<String, String>()
        val zo = o.getJSONObject("zvrsti")
        zo.keys().forEach { zv[it] = zo.getString(it) }
        val arr = o.getJSONArray("aplikacije")
        val apl = (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            Aplikacija(
                a.getString("p"), a.getString("i"), a.optString("o"), niz(a.getJSONArray("z")),
                a.optString("k").ifEmpty { null }, a.optLong("t"), a.optString("v"), a.optLong("c"),
                a.getString("d"), a.getString("s"), a.optLong("b"), niz(a.getJSONArray("g"))
            )
        }
        return Katalog(o.getLong("zig"), zv, apl)
    }
}
