package com.aurora.store.compose.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stolpci mreze aplikacij glede na sirino zaslona (TV, tablica, telefon) - en APK za vse naprave.
 * Robovi 16 dp, razmik 12 dp, kartica najvec 116 dp.
 */
data class SafeerMreza(val stolpci: Int, val sirina: Dp)

/** Cista funkcija (preizkus): sirina zaslona v dp -> stolpci in sirina kartice. */
fun safeerMreza(sirinaZaslonaDp: Int): SafeerMreza {
    val uporabno = (sirinaZaslonaDp - 32).coerceAtLeast(120)
    val stolpci = ((uporabno + 12) / 128).coerceIn(3, 7)
    val kartica = ((uporabno - 12 * (stolpci - 1)) / stolpci).coerceIn(72, 116)
    return SafeerMreza(stolpci, kartica.dp)
}

@Composable
fun rememberSafeerMreza(): SafeerMreza = safeerMreza(LocalConfiguration.current.screenWidthDp)
