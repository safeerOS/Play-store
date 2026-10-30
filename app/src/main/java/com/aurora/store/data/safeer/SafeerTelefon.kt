package com.aurora.store.data.safeer

import android.content.Context
import android.content.pm.PackageManager
import com.aurora.store.data.providers.NativeDeviceInfoProvider
import java.util.Properties

/**
 * Izhod v sili za televizor (lastnik 30. 10. 2026): privzeto vedno TV razlicica aplikacije. Ce Google za TV
 * odgovori »ni podprto« (AppNotSupported, npr. TikTok), uporabnik lahko izbere »Prenesi telefonsko razlicico«;
 * le za ta paket se nakup in prenos opravita s profilom telefona. Arhitektura (Platforms) in razlicica Androida
 * ostaneta od naprave, da Google poslje pakete, ki na tem televizorju tecejo.
 */
object SafeerTelefon {

    private const val NASTAVITVE = "safeer_telefonske_razlicice"
    private const val KLJUC = "paketi"

    /** Profil tablice/telefona z velikim zaslonom (vgrajen v knjiznico gplayapi). Tablica ima prednost pred telefonom zaradi vecjega zaslona in lezece postavitve. */
    private val PREDNOSTNI =
        listOf("tangorpro", "Pixel Tablet", "OnePlus8Pro", "px_9", "Pixel", "nothing")

    fun jeTelevizor(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun dovoljeno(context: Context, paket: String): Boolean =
        context.getSharedPreferences(NASTAVITVE, Context.MODE_PRIVATE)
            .getStringSet(KLJUC, emptySet()).orEmpty().contains(paket)

    fun dovoli(context: Context, paket: String) {
        val p = context.getSharedPreferences(NASTAVITVE, Context.MODE_PRIVATE)
        val novi = p.getStringSet(KLJUC, emptySet()).orEmpty().toMutableSet().apply { add(paket) }
        p.edit().putStringSet(KLJUC, novi).apply()
    }

    /** Ali je napaka Googlov »ni podprto za to napravo«. */
    fun niPodprto(napaka: String?): Boolean = napaka != null &&
        (
            napaka.contains("not supported", ignoreCase = true) ||
                napaka.contains("AppNotSupported")
            )

    /** Profil telefona z arhitekturo in Androidom te naprave. */
    fun lastnosti(context: Context, telefoni: List<Properties>): Properties {
        val izbran = PREDNOSTNI.firstNotNullOfOrNull { ime ->
            telefoni.firstOrNull {
                (
                    it.getProperty("Build.PRODUCT").orEmpty() +
                        it.getProperty("Build.DEVICE").orEmpty()
                    )
                    .contains(ime, ignoreCase = true)
            }
        } ?: telefoni.firstOrNull { it.getProperty("TouchScreen") == "3" }
            ?: throw IllegalStateException("ni profila telefona")
        val izid = Properties().apply { putAll(izbran) }
        val naprava = NativeDeviceInfoProvider.getNativeDeviceProperties(context)
        for (k in listOf(
            "Platforms",
            "Build.VERSION.SDK_INT",
            "Build.VERSION.RELEASE",
            "Locales",
            "TimeZone"
        )) {
            naprava.getProperty(k)?.let { izid.setProperty(k, it) }
        }
        return izid
    }
}
