package com.aurora.store.compose.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

private val FokusBarva = Color(0xFF7EDCAB)

/**
 * Safeer fokus okvir za vso Trgovino (daljinec): okoli elementa s fokusom narise jasen okvir v barvi Safeer.
 * Material gumbi, ikone in seznami (npr. podrobnosti aplikacije: Namesti, Rocni prenos) imajo sicer le
 * komaj viden poudarek (lastnik 30. 10. 2026: »fokus je slabo viden« na strani TikTok).
 * Okvir se rise le pri upravljanju s tipkami (daljinec, tipkovnica), na dotik ne.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafeerFokusOkvir(content: @Composable () -> Unit) {
    val nacin = LocalInputModeManager.current
    var meje by remember { mutableStateOf<Rect?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusedBoundsChanged { c -> meje = c?.takeIf { it.isAttached }?.boundsInRoot() }
            .drawWithContent {
                drawContent()
                val r = meje ?: return@drawWithContent
                if (nacin.inputMode != InputMode.Keyboard) return@drawWithContent
                // Vsebnik cez skoraj ves zaslon ni element za oznacevanje.
                if (r.width >= size.width * 0.95f &&
                    r.height >= size.height * 0.8f
                ) {
                    return@drawWithContent
                }
                if (r.width < 4f || r.height < 4f) return@drawWithContent
                val odmik = 3.dp.toPx()
                val debelina = 3.dp.toPx()
                drawRoundRect(
                    color = FokusBarva,
                    topLeft = Offset(r.left - odmik, r.top - odmik),
                    size = Size(r.width + 2 * odmik, r.height + 2 * odmik),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = debelina)
                )
            }
    ) {
        content()
    }
}
