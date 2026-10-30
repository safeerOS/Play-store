package com.aurora.store.data.safeer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Razdelek »Safeer« v Safeer Play Store: nase aplikacije (Safeer OS, Safeer TV Browser, Safeer Predvajalnik)
 * iz uradnih izdaj na GitHubu (safeerOS/Android-tv).
 *
 * Po vzoru Komi Store (Apache-2.0, github.com/komi-store/komi-store: InstalledReleaseBinding, AndroidApkInspector)
 * in F-Droida: pred namestitvijo preverimo kontrolno vsoto SHA-256 iz izdaje in da je paket podpisan
 * z Safeer kljucem. Tuj ali spremenjen paket se ne namesti.
 */
object SafeerIzdaje {

    private const val TAG = "SafeerIzdaje"
    const val VIR = "https://api.github.com/repos/safeerOS/Android-tv/releases/latest"
    private const val PRENOSI = "https://github.com/safeerOS/Android-tv/releases/download/"
    const val EXTRA_PAKET = "safeer_paket"

    /** SHA-256 Safeer podpisnega potrdila (isti kljuc kot Safeer OS in Safeer Play Store). */
    const val NAS_KLJUC = "e7808780c6f492adc60036be680667cf74bde44164b5c63179e1d0f7bdfd7e16"

    /** Seznam izdaj hranimo uro: GitHub brez prijave dovoli 60 poizvedb na uro z enega naslova. */
    private const val SVEZE_MS = 60 * 60 * 1000L

    enum class Naprava { TV, TELEFON, TABLICA }

    data class Paket(
        val ime: String,
        val paket: String,
        val verzija: String,
        val url: String,
        val sha256: String?,
        val velikost: Long
    )

    private data class Vzorec(
        val predpona: String,
        val ime: String,
        val paket: String,
        val naprava: Naprava
    )

    private val VZORCI = listOf(
        Vzorec("safeer-os-tv-", "Safeer OS", "si.safeer.os", Naprava.TV),
        Vzorec("safeer-os-mobile-", "Safeer OS", "si.safeer.phone", Naprava.TELEFON),
        Vzorec("safeer-os-tablet-", "Safeer OS", "si.safeer.tablet", Naprava.TABLICA),
        Vzorec("safeer-browser-tv-", "Safeer TV Browser", "si.safeer.tv", Naprava.TV),
        Vzorec("safeer-predvajalnik-", "Safeer Predvajalnik", "si.safeer.player", Naprava.TV)
    )

    private val VERZIJA = Regex("""\d+(\.\d+){0,4}""")

    private val _sporocila = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Stanje prenosa ali namestitve po paketih (za prikaz v vrstici). */
    val sporocila: StateFlow<Map<String, String>> = _sporocila.asStateFlow()

    fun sporoci(paket: String, besedilo: String) {
        _sporocila.update { it + (paket to besedilo) }
    }

    fun naprava(context: Context): Naprava {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return Naprava.TV
        return if (context.resources.configuration.smallestScreenWidthDp >=
            600
        ) {
            Naprava.TABLICA
        } else {
            Naprava.TELEFON
        }
    }

    /** Iz odgovora GitHuba (releases/latest) izbere pakete za to napravo. Cista funkcija (preizkusi). */
    fun razberi(json: String, naprava: Naprava): List<Paket> {
        val sredstva = JSONObject(json).optJSONArray("assets") ?: return emptyList()
        val izid = mutableListOf<Paket>()
        for (i in 0 until sredstva.length()) {
            val a = sredstva.optJSONObject(i) ?: continue
            val ime = a.optString("name")
            if (!ime.endsWith(".apk")) continue
            val v =
                VZORCI.firstOrNull { ime.startsWith(it.predpona) && it.naprava == naprava }
                    ?: continue
            val verzija = ime.removePrefix(v.predpona).removeSuffix(".apk")
            if (!VERZIJA.matches(verzija)) continue
            val url = a.optString("browser_download_url")
            if (!url.startsWith(PRENOSI)) continue
            val digest = a.optString("digest").takeIf {
                it.startsWith("sha256:")
            }?.removePrefix("sha256:")?.lowercase()
            izid += Paket(v.ime, v.paket, verzija, url, digest, a.optLong("size"))
        }
        return izid.distinctBy { it.paket }
    }

    /** Ali je izdaja [na] novejsa od namescene [imam] (stevilke loci pika; pripone kot -beta zanemarimo). */
    fun novejsa(na: String, imam: String?): Boolean {
        if (imam.isNullOrBlank()) return true
        fun deli(s: String) = s.takeWhile { it.isDigit() || it == '.' }.split('.').map {
            it.toIntOrNull()
                ?: 0
        }
        val a = deli(na)
        val b = deli(imam)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun namescena(context: Context, paket: String): String? = try {
        context.packageManager.getPackageInfo(paket, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun nalozi(context: Context, naprava: Naprava, sveze: Boolean): List<Paket> {
        val f = File(context.cacheDir, "safeer-izdaje.json")
        val json = if (!sveze &&
            f.isFile &&
            System.currentTimeMillis() - f.lastModified() in 0..SVEZE_MS
        ) {
            f.readText()
        } else {
            try {
                besedilo(VIR).also { f.writeText(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Izdaje z GitHuba niso dosegljive: ${e.javaClass.simpleName}")
                if (f.isFile) f.readText() else throw e
            }
        }
        return razberi(json, naprava)
    }

    private fun povezava(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Safeer-Play-Store")
        }

    private fun besedilo(url: String): String {
        val c = povezava(url)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) throw IOException("GitHub ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** Prenese paket, preveri kontrolno vsoto in Safeer podpis. Vrne datoteko, pripravljeno za namestitev. */
    fun prenesiInPreveri(context: Context, p: Paket, napredek: (Int) -> Unit): File {
        val mapa = File(context.cacheDir, "safeer-apk").apply { mkdirs() }
        mapa.listFiles()?.forEach { it.delete() }
        val cilj = File(mapa, "${p.paket}-${p.verzija}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        val c = povezava(p.url)
        try {
            if (c.responseCode != 200) throw IOException("Prenos ${c.responseCode}")
            val skupaj = c.contentLengthLong.takeIf { it > 0 } ?: p.velikost
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
                        if (skupaj > 0) {
                            val odstotek = (preneseno * 100 / skupaj).toInt()
                            if (odstotek != zadnji) {
                                zadnji = odstotek
                                napredek(odstotek)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        val vsota = hex(md.digest())
        if (p.sha256 != null && vsota != p.sha256) {
            cilj.delete()
            throw SecurityException("kontrolna vsota se ne ujema z izdajo")
        }
        try {
            preveriPodpis(context, cilj, p.paket)
        } catch (e: SecurityException) {
            cilj.delete()
            throw e
        }
        return cilj
    }

    @Suppress("DEPRECATION")
    private fun preveriPodpis(context: Context, f: File, paket: String) {
        val pm = context.packageManager
        val zastavice = PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
        val info: PackageInfo = pm.getPackageArchiveInfo(f.path, zastavice)
            ?: throw SecurityException("datoteka ni veljaven paket")
        if (info.packageName != paket) throw SecurityException("napačen paket ${info.packageName}")
        val podpisi = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.let { podpisi += it }
        info.signatures?.let { podpisi += it }
        val sha = MessageDigest.getInstance("SHA-256")
        if (podpisi.isEmpty() || podpisi.none { hex(sha.digest(it.toByteArray())) == NAS_KLJUC }) {
            throw SecurityException("paket ni podpisan s Safeer ključem")
        }
    }

    /** Namestitev prek sistemskega namescevalnika; sistem uporabnika vprasa za potrditev. */
    fun namesti(context: Context, f: File, paket: String) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(paket)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { seja ->
            f.inputStream().use { vhod ->
                seja.openWrite("safeer.apk", 0, f.length()).use { izhod ->
                    vhod.copyTo(izhod)
                    seja.fsync(izhod)
                }
            }
            val namera = Intent(context, SafeerNamestitevReceiver::class.java)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PAKET, paket)
            val zast = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            seja.commit(PendingIntent.getBroadcast(context, id, namera, zast).intentSender)
        }
    }
}
