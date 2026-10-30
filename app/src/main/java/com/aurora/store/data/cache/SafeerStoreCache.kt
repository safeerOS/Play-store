/*
 * SPDX-FileCopyrightText: 2026 Safeer OS & Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.data.cache

import android.content.Context
import android.util.LruCache
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.Artwork
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import com.aurora.store.AuroraApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Trajni predpomnilnik trgovine aplikacij (Safeer Trgovina) z:
 * 1. GEOLOKACIJSKO / DRŽAVNO PRILAGODITVIJO (Slovenija, Hrvaška, DACH, Globalno):
 *    - RTV 365, Voyo SI, EON SI se ponudijo le v Sloveniji
 *    - Za druge države se samodejno ponudijo lokalne TV aplikacije te države (ARD/ZDF za DE, HRTi za HR...)
 * 2. ZAŠČITO PRED PREZASEDENOSTJO POMNILNIKA (RAM & DISK):
 *    - Omejen LruCache v RAM-u (največ 2 polici, 4 lestvice, 6 kategorij)
 *    - Samodejno sproščanje ob opozorilu sistema (onLowMemory/onTrimMemory -> sprostiRam())
 *    - Diskovni predpomnilnik z avtomatskim obrezovanjem (največ 25 datotek v cacheDir)
 */
object SafeerStoreCache {

    private val delavec = Executors.newSingleThreadExecutor()

    // 1. Nivo: Striktno omejen LruCache v RAM-u (preprečuje kopičenje objektov v heapu)
    private val ramHome = LruCache<String, StreamBundle>(2)
    private val ramTopCharts = LruCache<String, StreamCluster>(4)
    private val ramCategoryStreams = LruCache<String, StreamBundle>(6)
    private val ramCategories = LruCache<String, List<Category>>(2)

    /**
     * Določi kodo države uporabnika glede na sistemsko regijo, jezik ali ročno nastavitev.
     */
    fun trenutnaDrzava(): String {
        return try {
            val prefs = AuroraApp.appContext.getSharedPreferences("com.aurora.store_preferences", Context.MODE_PRIVATE)
            val spoof = prefs.getString("LOCALE_SPOOF_COUNTRY", null)
            if (!spoof.isNullOrBlank()) return spoof.uppercase()
            val c = Locale.getDefault().country.uppercase()
            if (c.isNotBlank()) return c
            val l = Locale.getDefault().language.lowercase()
            when (l) {
                "sl" -> "SI"
                "hr", "bs", "sr" -> "HR"
                "de" -> "DE"
                "it" -> "IT"
                "fr" -> "FR"
                "es" -> "ES"
                else -> "US"
            }
        } catch (_: Exception) {
            "SI"
        }
    }

    /**
     * Takojšnje sproščanje celotnega RAM predpomnilnika ob opozorilu Android OS (onLowMemory/onTrimMemory).
     * Podatki na disku ostanejo nedotaknjeni in varni!
     */
    fun sprostiRam() {
        try {
            ramHome.evictAll()
            ramTopCharts.evictAll()
            ramCategoryStreams.evictAll()
            ramCategories.evictAll()
        } catch (_: Exception) {}
    }

    private fun mapa(): File? = try {
        File(AuroraApp.appContext.cacheDir, "safeer-trgovina-predpomnilnik").apply { mkdirs() }
    } catch (_: Exception) {
        null
    }

    private fun obreziDiskovniPredpomnilnik() {
        try {
            val m = mapa() ?: return
            val datoteke = m.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
            if (datoteke.size > 25) {
                val zaIzbris = datoteke.take(datoteke.size - 25)
                zaIzbris.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    private fun kljucUrl(url: String): String =
        url.replace(Regex("[^a-zA-Z0-9_]"), "_").takeLast(64).lowercase()

    // --- ZASILNI SEZNAMI PRILAGOJENI ZA DRŽAVO UPORABNIKA ---

    private fun zasilnaApp(pkg: String, ime: String, razvijalec: String): App = App(
        id = pkg.hashCode(),
        packageName = pkg,
        displayName = ime,
        developerName = razvijalec,
        iconArtwork = Artwork(url = "")
    )

    private fun lokaliziraneAplikacijeZaDrzavo(drzava: String): List<App> {
        return when (drzava) {
            "SI" -> listOf(
                zasilnaApp("si.rtvslo.rtv365", "RTV 365", "RTV Slovenija"),
                zasilnaApp("si.proplus.voyo", "VOYO.si", "Pro Plus d.o.o."),
                zasilnaApp("com.ug.eon.android.tv", "EON TV", "United Group B.V."),
                zasilnaApp("net.marand.tv2go.client.tv", "T-2 tv2go", "T-2 d.o.o."),
                zasilnaApp("com.a1.xploretv.si", "A1 Xplore TV SI", "A1 Slovenija"),
                zasilnaApp("si.telekom.neo.android.tv", "NEO TV Lite", "Telekom Slovenije"),
                zasilnaApp("com.google.android.youtube.tv", "YouTube TV", "Google LLC"),
                zasilnaApp("org.videolan.vlc", "VLC for Android", "Videolabs"),
                zasilnaApp("org.xbmc.kodi", "Kodi", "Kodi Foundation"),
                zasilnaApp("com.netflix.ninja", "Netflix", "Netflix, Inc.")
            )
            "HR" -> listOf(
                zasilnaApp("hr.hrt.hrti.ott", "HRTi OTT", "Hrvatska radiotelevizija"),
                zasilnaApp("hr.t.ht.maxtv.ott.tv", "MAXtv to Go", "Hrvatski Telekom"),
                zasilnaApp("hr.rtl.play", "Voyo HR", "RTL Hrvatska"),
                zasilnaApp("com.ug.eon.android.tv", "EON TV", "United Group B.V."),
                zasilnaApp("com.a1.xploretv.hr", "A1 Xplore TV HR", "A1 Hrvatska"),
                zasilnaApp("com.google.android.youtube.tv", "YouTube TV", "Google LLC"),
                zasilnaApp("org.videolan.vlc", "VLC for Android", "Videolabs"),
                zasilnaApp("org.xbmc.kodi", "Kodi", "Kodi Foundation"),
                zasilnaApp("com.netflix.ninja", "Netflix", "Netflix, Inc.")
            )
            "DE", "AT", "CH" -> listOf(
                zasilnaApp("de.swr.avp.ard.tv", "ARD Mediathek", "ARD Online"),
                zasilnaApp("com.zdf.android.mediathek.tv", "ZDFmediathek", "ZDF"),
                zasilnaApp("de.prosiebensat1digital.joyn", "Joyn", "Joyn GmbH"),
                zasilnaApp("de.cbc.videoplus", "RTL+", "RTL interactive GmbH"),
                zasilnaApp("at.orf.tvthek", "ORF ON", "ORF"),
                zasilnaApp("com.google.android.youtube.tv", "YouTube TV", "Google LLC"),
                zasilnaApp("org.videolan.vlc", "VLC for Android", "Videolabs"),
                zasilnaApp("org.xbmc.kodi", "Kodi", "Kodi Foundation"),
                zasilnaApp("com.netflix.ninja", "Netflix", "Netflix, Inc.")
            )
            else -> listOf(
                zasilnaApp("com.google.android.youtube.tv", "YouTube TV", "Google LLC"),
                zasilnaApp("com.netflix.ninja", "Netflix", "Netflix, Inc."),
                zasilnaApp("com.disney.disneyplus", "Disney+", "Disney"),
                zasilnaApp("com.spotify.tv.android", "Spotify", "Spotify AB"),
                zasilnaApp("org.videolan.vlc", "VLC for Android", "Videolabs"),
                zasilnaApp("org.xbmc.kodi", "Kodi", "Kodi Foundation"),
                zasilnaApp("com.plexapp.android", "Plex", "Plex, Inc."),
                zasilnaApp("tv.twitch.android.app", "Twitch", "Twitch Interactive, Inc.")
            )
        }
    }

    // --- ZASILNI TAKOJŠNJI PRIKAZ (0 ms, kadar disk datoteka še ne obstaja) ---

    fun zasilniTopChart(type: TopChartsContract.Type, chart: TopChartsContract.Chart): StreamCluster {
        val cat = if (type == TopChartsContract.Type.GAME) StreamContract.Category.GAME else StreamContract.Category.APPLICATION
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${cat.name}"
        val home = ramHome.get(kljuc) ?: beriHomeBundle(cat)
        val apps = home?.streamClusters?.values
            ?.flatMap { it.clusterAppList }
            ?.distinctBy { it.packageName }
            ?.take(30)
            ?.ifEmpty { null }
            ?: lokaliziraneAplikacijeZaDrzavo(drzava)

        val naslov = when (chart) {
            TopChartsContract.Chart.TOP_SELLING_FREE -> "Najboljše brezplačne"
            TopChartsContract.Chart.TOP_GROSSING -> "Najbolj donosne"
            TopChartsContract.Chart.MOVERS_SHAKERS -> "V vzponu"
            TopChartsContract.Chart.TOP_SELLING_PAID -> "Najboljše plačljive"
            else -> "Lestvica"
        }
        return StreamCluster(
            id = chart.name.hashCode(),
            clusterTitle = naslov,
            clusterSubtitle = "",
            clusterBrowseUrl = "",
            clusterNextPageUrl = "",
            clusterAppList = apps
        )
    }

    fun zasilniCategoryBundle(browseUrl: String): StreamBundle {
        val cat = if (browseUrl.contains("GAME", ignoreCase = true)) StreamContract.Category.GAME else StreamContract.Category.APPLICATION
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${cat.name}"
        val home = ramHome.get(kljuc) ?: beriHomeBundle(cat)
        if (home != null && home.hasCluster()) {
            val omejeneGruce = home.streamClusters.entries.take(3).associate { (k, cluster) ->
                k to cluster.copy(clusterAppList = cluster.clusterAppList.take(12))
            }
            return home.copy(streamClusters = omejeneGruce)
        }
        val defaultApps = lokaliziraneAplikacijeZaDrzavo(drzava)
        return StreamBundle(
            id = 1,
            streamClusters = mapOf(
                1 to StreamCluster(
                    id = 1,
                    clusterTitle = "Priljubljene aplikacije",
                    clusterSubtitle = "",
                    clusterBrowseUrl = browseUrl,
                    clusterNextPageUrl = "",
                    clusterAppList = defaultApps
                )
            ),
            streamNextPageUrl = ""
        )
    }

    // --- HOME STREAM BUNDLE (Igre / Aplikacije domače police) ---

    fun beriHomeBundle(category: StreamContract.Category): StreamBundle? {
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${category.name}"
        ramHome.get(kljuc)?.let { return it }
        return try {
            val datoteka = File(mapa() ?: return null, "home_${drzava.lowercase()}_${category.name.lowercase()}.json")
            if (datoteka.isFile && datoteka.length() > 0) {
                val bundle = bundleIzJson(JSONObject(datoteka.readText()))
                if (bundle.hasCluster()) {
                    ramHome.put(kljuc, bundle)
                    bundle
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun shraniHomeBundle(category: StreamContract.Category, bundle: StreamBundle) {
        if (!bundle.hasCluster()) return
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${category.name}"
        ramHome.put(kljuc, bundle)
        delavec.execute {
            try {
                val datoteka = File(mapa() ?: return@execute, "home_${drzava.lowercase()}_${category.name.lowercase()}.json")
                datoteka.writeText(bundleVJson(bundle).toString())
                obreziDiskovniPredpomnilnik()
            } catch (_: Exception) {}
        }
    }

    // --- TOP CHARTS (Lestvice brezplačnih, donosnih, v trendu...) ---

    private fun kljucTopChart(type: TopChartsContract.Type, chart: TopChartsContract.Chart) =
        "${trenutnaDrzava()}_${type.name.lowercase()}_${chart.name.lowercase()}"

    fun beriTopChart(type: TopChartsContract.Type, chart: TopChartsContract.Chart): StreamCluster {
        val kljuc = kljucTopChart(type, chart)
        ramTopCharts.get(kljuc)?.let { return it }
        try {
            val datoteka = File(mapa() ?: return zasilniTopChart(type, chart), "chart_${kljuc.lowercase()}.json")
            if (datoteka.isFile && datoteka.length() > 0) {
                val cluster = clusterIzJson(JSONObject(datoteka.readText()))
                if (cluster.clusterAppList.isNotEmpty()) {
                    ramTopCharts.put(kljuc, cluster)
                    return cluster
                }
            }
        } catch (_: Exception) {}
        return zasilniTopChart(type, chart)
    }

    fun shraniTopChart(type: TopChartsContract.Type, chart: TopChartsContract.Chart, cluster: StreamCluster) {
        if (cluster.clusterAppList.isEmpty()) return
        val kljuc = kljucTopChart(type, chart)
        ramTopCharts.put(kljuc, cluster)
        delavec.execute {
            try {
                val datoteka = File(mapa() ?: return@execute, "chart_${kljuc.lowercase()}.json")
                datoteka.writeText(clusterVJson(cluster).toString())
                obreziDiskovniPredpomnilnik()
            } catch (_: Exception) {}
        }
    }

    // --- CATEGORY STREAMS (Zvrsti: Komunikacija, Glasba in zvok, Nakupovanje...) ---

    fun beriCategoryBundle(browseUrl: String): StreamBundle {
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${kljucUrl(browseUrl)}"
        ramCategoryStreams.get(kljuc)?.let { return it }
        try {
            val datoteka = File(mapa() ?: return zasilniCategoryBundle(browseUrl), "cat_${kljuc.lowercase()}.json")
            if (datoteka.isFile && datoteka.length() > 0) {
                val bundle = bundleIzJson(JSONObject(datoteka.readText()))
                if (bundle.hasCluster()) {
                    ramCategoryStreams.put(kljuc, bundle)
                    return bundle
                }
            }
        } catch (_: Exception) {}
        return zasilniCategoryBundle(browseUrl)
    }

    fun shraniCategoryBundle(browseUrl: String, bundle: StreamBundle) {
        if (!bundle.hasCluster()) return
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${kljucUrl(browseUrl)}"
        ramCategoryStreams.put(kljuc, bundle)
        delavec.execute {
            try {
                val datoteka = File(mapa() ?: return@execute, "cat_${kljuc.lowercase()}.json")
                datoteka.writeText(bundleVJson(bundle).toString())
                obreziDiskovniPredpomnilnik()
            } catch (_: Exception) {}
        }
    }

    // --- KATEGORIJE / ZVRSTI (Akcija, Strategije, Orodja...) ---

    fun beriKategorije(type: Category.Type): List<Category>? {
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${type.name}"
        ramCategories.get(kljuc)?.let { return it }
        return try {
            val datoteka = File(mapa() ?: return null, "categories_${drzava.lowercase()}_${type.name.lowercase()}.json")
            if (datoteka.isFile && datoteka.length() > 0) {
                val arr = JSONArray(datoteka.readText())
                val list = List(arr.length()) { categoryIzJson(arr.getJSONObject(it)) }
                if (list.isNotEmpty()) {
                    ramCategories.put(kljuc, list)
                    list
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun shraniKategorije(type: Category.Type, list: List<Category>) {
        if (list.isEmpty()) return
        val drzava = trenutnaDrzava()
        val kljuc = "${drzava}_${type.name}"
        ramCategories.put(kljuc, list)
        delavec.execute {
            try {
                val datoteka = File(mapa() ?: return@execute, "categories_${drzava.lowercase()}_${type.name.lowercase()}.json")
                val arr = JSONArray()
                list.forEach { arr.put(categoryVJson(it)) }
                datoteka.writeText(arr.toString())
                obreziDiskovniPredpomnilnik()
            } catch (_: Exception) {}
        }
    }

    // --- JSON PRETVORBE ---

    fun appVJson(a: App): JSONObject = JSONObject()
        .put("id", a.id)
        .put("packageName", a.packageName)
        .put("displayName", a.displayName)
        .put("developerName", a.developerName)
        .put("versionCode", a.versionCode)
        .put("versionName", a.versionName)
        .put("iconUrl", a.iconArtwork.url)
        .put("shortDescription", a.shortDescription)
        .put("description", a.description)
        .put("isFree", a.isFree)
        .put("price", a.price)
        .put("containsAds", a.containsAds)
        .put("isInstalled", a.isInstalled)
        .put("size", a.size)
        .put("updatedOn", a.updatedOn)
        .put("labeledRating", a.labeledRating)
        .put("downloadString", a.downloadString)

    fun appIzJson(o: JSONObject): App = App(
        id = o.optInt("id"),
        packageName = o.optString("packageName"),
        displayName = o.optString("displayName"),
        developerName = o.optString("developerName"),
        versionCode = o.optLong("versionCode"),
        versionName = o.optString("versionName"),
        iconArtwork = Artwork(url = o.optString("iconUrl")),
        shortDescription = o.optString("shortDescription"),
        description = o.optString("description"),
        isFree = o.optBoolean("isFree", true),
        price = o.optString("price"),
        containsAds = o.optBoolean("containsAds", false),
        isInstalled = o.optBoolean("isInstalled", false),
        size = o.optLong("size", 0L),
        updatedOn = o.optString("updatedOn"),
        labeledRating = o.optString("labeledRating", "4.0"),
        downloadString = o.optString("downloadString")
    )

    fun clusterVJson(c: StreamCluster): JSONObject = JSONObject()
        .put("id", c.id)
        .put("title", c.clusterTitle)
        .put("subtitle", c.clusterSubtitle)
        .put("browseUrl", c.clusterBrowseUrl)
        .put("nextPageUrl", c.clusterNextPageUrl)
        .put("apps", JSONArray().apply { c.clusterAppList.forEach { put(appVJson(it)) } })

    fun clusterIzJson(o: JSONObject): StreamCluster {
        val a = o.optJSONArray("apps") ?: JSONArray()
        val apps = List(a.length()) { appIzJson(a.getJSONObject(it)) }
        return StreamCluster(
            id = o.optInt("id"),
            clusterTitle = o.optString("title"),
            clusterSubtitle = o.optString("subtitle"),
            clusterBrowseUrl = o.optString("browseUrl"),
            clusterNextPageUrl = o.optString("nextPageUrl"),
            clusterAppList = apps
        )
    }

    fun bundleVJson(b: StreamBundle): JSONObject {
        val clustersObj = JSONObject()
        b.streamClusters.forEach { (k, v) ->
            clustersObj.put(k.toString(), clusterVJson(v))
        }
        return JSONObject()
            .put("id", b.id)
            .put("nextPageUrl", b.streamNextPageUrl)
            .put("clusters", clustersObj)
    }

    fun bundleIzJson(o: JSONObject): StreamBundle {
        val clustersObj = o.optJSONObject("clusters") ?: JSONObject()
        val map = mutableMapOf<Int, StreamCluster>()
        val keys = clustersObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val cObj = clustersObj.getJSONObject(key)
            val intKey = key.toIntOrNull() ?: map.size
            map[intKey] = clusterIzJson(cObj)
        }
        return StreamBundle(
            id = o.optInt("id"),
            streamClusters = map,
            streamNextPageUrl = o.optString("nextPageUrl")
        )
    }

    fun categoryVJson(c: Category): JSONObject = JSONObject()
        .put("title", c.title)
        .put("browseUrl", c.browseUrl)
        .put("imageUrl", c.imageUrl)
        .put("type", c.type.name)

    fun categoryIzJson(o: JSONObject): Category = Category(
        title = o.optString("title"),
        browseUrl = o.optString("browseUrl"),
        imageUrl = o.optString("imageUrl"),
        type = try { Category.Type.valueOf(o.optString("type")) } catch (_: Exception) { Category.Type.APPLICATION }
    )
}
