/*
 * SPDX-FileCopyrightText: 2026 Safeer OS & Aurora OSS
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.composable.SafeerMint
import com.aurora.store.data.room.favourite.Favourite
import com.aurora.store.data.room.favourite.FavouriteEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Safeer OS Media Center poster card for Aurora Store:
 * Combines modern TV card design (112dp width, sleek rounded pill corners,
 * rich 2dp mint focus border on D-pad navigation, crisp 2-line title, and
 * background card lift mirroring GlasbaActivity.cistPlakat).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    app: App,
    sirina: androidx.compose.ui.unit.Dp = 116.dp,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isFav by remember(app.packageName) { mutableStateOf(false) }

    LaunchedEffect(app.packageName) {
        try {
            val dao = EntryPointAccessors.fromApplication(context.applicationContext, FavouriteEntryPoint::class.java).favouriteDao()
            isFav = dao.isFavourite(app.packageName)
        } catch (_: Exception) {}
    }

    fun toggleFavourite() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val dao = EntryPointAccessors.fromApplication(context.applicationContext, FavouriteEntryPoint::class.java).favouriteDao()
                val trenutno = dao.isFavourite(app.packageName)
                if (trenutno) {
                    dao.delete(app.packageName)
                    withContext(Dispatchers.Main) {
                        isFav = false
                        Toast.makeText(context, "${app.displayName}: odstranjeno iz priljubljenih", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dao.insert(Favourite.fromApp(app, Favourite.Mode.MANUAL))
                    withContext(Dispatchers.Main) {
                        isFav = true
                        Toast.makeText(context, "${app.displayName}: dodano med priljubljene ❤️", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val cardShape = RoundedCornerShape(16.dp)
    val cardBg = if (isFocused) Color(0x332DD4BF) else Color(0x28FFFFFF)
    val borderStroke = if (isFocused) {
        BorderStroke(2.dp, SafeerMint)
    } else {
        BorderStroke(1.dp, Color(0x22FFFFFF))
    }

    Column(
        modifier = modifier
            .width(sirina)
            .clip(cardShape)
            .background(cardBg)
            .border(borderStroke, cardShape)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Menu || keyEvent.key == Key.ButtonX || keyEvent.key == Key.ButtonY)
                ) {
                    toggleFavourite()
                    true
                } else false
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { toggleFavourite() }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App ikona v obliki elegantne ploščice (squircle 14dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33000000)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.iconArtwork.url)
                    .crossfade(true)
                    .build(),
                contentDescription = app.displayName,
                contentScale = ContentScale.Crop
            )

            if (isFav) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("❤️", fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Ime aplikacije (vedno 2 vrstici, da so vse kartice poravnane kot v filmih in serijah)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = app.displayName,
            color = if (isFocused) SafeerMint else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}
