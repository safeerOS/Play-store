/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Whether the active [MaterialTheme] is dark.
 *
 * Derived from the resolved color scheme rather than [androidx.compose.foundation.isSystemInDarkTheme]
 * so it stays correct when the user forces a light/dark theme that differs from the system setting.
 */
@Composable
@ReadOnlyComposable
private fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Amber used to flag warnings/caveats. Lightened in dark theme for adequate contrast.
 */
val warningColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFFF7600)

/**
 * Green used to flag positive/success states. Lightened in dark theme for adequate contrast.
 */
val successColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme()) Color(0xFF5BD27A) else Color(0xFF1B8738)

val colorGreen: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme()) Color(0xFF81C784) else Color(0xFF388E3C)

val colorRed: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme()) Color(0xFFE57373) else Color(0xFFD32F2F)

/**
 * Brand color schemes seeded from Safeer OS turquoise accent (#2DD4BF), used on devices that don't support
 * dynamic color (Android 11 and below) so the full palette stays consistent with Safeer OS.
 */
val BrandLightColorScheme = lightColorScheme(
    primary = Color(0xFF0D9488),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF99F6E4),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF4B635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E2),
    onSecondaryContainer = Color(0xFF07201D),
    tertiary = Color(0xFF436278),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC7E7FF),
    onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFF0D1214),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0D1214),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF1B2327),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val BrandDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF0F766E),
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCDE8E2),
    tertiary = Color(0xFFAECBE4),
    onTertiary = Color(0xFF133448),
    tertiaryContainer = Color(0xFF2B4A5F),
    onTertiaryContainer = Color(0xFFC7E7FF),
    background = Color(0xFF0D1214),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0D1214),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF1B2327),
    onSurfaceVariant = Color(0xFFBFC9C5),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)
