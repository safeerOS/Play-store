/*
 * SPDX-FileCopyrightText: 2026 Safeer OS & Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.gplayapi.data.models.Category
import com.aurora.store.R

val SafeerMint = Color(0xFF2DD4BF)
val SafeerDarkBg = Color(0xFF0D1214)

@Composable
fun SafeerStoreTopBar(
    selectedType: Int, // 1 = Igre, 0 = Aplikacije, 2 = Posodobitve, 3 = Safeer
    // 0 Priporoceno, 1 Priljubljene, 2 brezplacne, 3 v trendu, 4 donos, 5 placljive
    selectedChartIndex: Int,
    selectedCategory: Category?,
    categories: List<Category>,
    updateCount: Int,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onTypeSelected: (Int) -> Unit,
    onChartSelected: (Int) -> Unit,
    onCategorySelected: (Category?) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTypeDialog by remember { mutableStateOf(false) }
    var showChartDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    val oznakaIgre = stringResource(R.string.safeer_type_games)
    val oznakaAplikacije = stringResource(R.string.safeer_type_apps)
    val oznakaPosodobitve = if (updateCount > 0) {
        stringResource(R.string.safeer_type_updates_count, updateCount)
    } else {
        stringResource(R.string.safeer_type_updates)
    }
    val typeOptions = listOf(
        1 to oznakaIgre,
        0 to oznakaAplikacije,
        2 to oznakaPosodobitve,
        3 to "🛡️ Safeer",
        4 to "🤖 F-Droid"
    )

    val oznakaPriporoceno = stringResource(R.string.safeer_chart_recommended)
    val chartOptions = listOf(
        0 to oznakaPriporoceno,
        1 to stringResource(R.string.safeer_chart_favourites),
        2 to stringResource(R.string.safeer_chart_top_free),
        3 to stringResource(R.string.safeer_chart_trending),
        4 to stringResource(R.string.safeer_chart_top_grossing),
        5 to stringResource(R.string.safeer_chart_top_paid)
    )

    val currentTypeLabel = when (selectedType) {
        1 -> oznakaIgre
        0 -> oznakaAplikacije
        3 -> "🛡️ Safeer"
        4 -> "🤖 F-Droid"
        else -> oznakaPosodobitve
    }

    val oznakaVseZvrsti = stringResource(R.string.safeer_all_categories)
    val currentChartLabel =
        chartOptions.firstOrNull { it.first == selectedChartIndex }?.second ?: oznakaPriporoceno
    val currentCategoryLabel = selectedCategory?.title?.ifBlank { null } ?: oznakaVseZvrsti

    val imenaZvrsti = listOf(
        stringResource(R.string.safeer_cat_communication),
        stringResource(R.string.safeer_cat_music),
        stringResource(R.string.safeer_cat_shopping),
        stringResource(R.string.safeer_cat_entertainment),
        stringResource(R.string.safeer_cat_books),
        stringResource(R.string.safeer_cat_house),
        stringResource(R.string.safeer_cat_travel)
    )
    val prednostneKategorije = remember(categories, imenaZvrsti) {
        val preslikave = listOf(
            imenaZvrsti[0] to listOf("Komunikacija", "Communication", "COMMUNICATION"),
            imenaZvrsti[1] to
                listOf(
                    "Glasba in zvok",
                    "Glasba",
                    "Music & Audio",
                    "Music and Audio",
                    "MUSIC_AND_AUDIO"
                ),
            imenaZvrsti[2] to listOf("Nakupovanje", "Shopping", "SHOPPING"),
            imenaZvrsti[3] to listOf("Razvedrilo", "Zabava", "Entertainment", "ENTERTAINMENT"),
            imenaZvrsti[4] to
                listOf("Knjige in viri", "Knjige", "Books & Reference", "BOOKS_AND_REFERENCE"),
            imenaZvrsti[5] to
                listOf("Vse o vašem domu", "Dom", "House & Home", "HOUSE_AND_HOME"),
            imenaZvrsti[6] to
                listOf(
                    "Turistične in lokalne informacije",
                    "Potovanja in lokalno",
                    "Travel & Local",
                    "TRAVEL_AND_LOCAL"
                )
        )
        val prioriteta = mutableListOf<Category>()
        val zeDodane = mutableSetOf<String>()
        for ((sloIme, kljuci) in preslikave) {
            val najdena = categories.find { c ->
                kljuci.any { k ->
                    c.title.equals(k, ignoreCase = true) ||
                        c.type.name.replace("_", " ").equals(k, ignoreCase = true) ||
                        c.type.name.equals(k, ignoreCase = true)
                }
            }
            if (najdena != null) {
                prioriteta.add(
                    Category(
                        title = sloIme,
                        browseUrl = najdena.browseUrl,
                        imageUrl = najdena.imageUrl,
                        type = najdena.type
                    )
                )
                zeDodane.add(najdena.browseUrl)
            }
        }
        // Vse ostale zvrsti za prednostnimi. Primerjamo po naslovu zvrsti (browseUrl): vrsta (APPLICATION/GAME)
        // je pri vseh zvrsteh enaka, zato je prej izlocila vse razen prednostnih.
        for (c in categories) {
            if (c.browseUrl !in zeDodane) {
                prioriteta.add(c)
                zeDodane.add(c.browseUrl)
            }
        }
        prioriteta
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Telefon/tablica: izbirni gumbi v vodoravno pomicni vrsti, da ikone desno ostanejo na zaslonu.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1) Izbira vrste (Igre | Aplikacije | Posodobitve)
            SafeerTopBarChip(
                label = currentTypeLabel,
                onClick = { showTypeDialog = true },
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )

            Spacer(Modifier.width(8.dp))

            // Ce smo v Posodobitvah, lestvica in zvrst nista smiselni
            if (selectedType in 0..1) {
                // 2) Razvrstitev / Lestvice (Priporoceno, Priljubljene, Brezplacne, Trending...)
                SafeerTopBarChip(
                    label = currentChartLabel,
                    onClick = { showChartDialog = true }
                )

                Spacer(Modifier.width(8.dp))

                // 3) Zvrsti / Kategorije
                SafeerTopBarChip(
                    label = currentCategoryLabel,
                    onClick = { showCategoryDialog = true }
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 4) Gumbi na desni: Iskanje, Prenosi, Meni
        SafeerTopBarIconButton(
            iconRes = R.drawable.ic_round_search,
            contentDescription = stringResource(R.string.action_search),
            onClick = onSearchClick
        )

        Spacer(Modifier.width(6.dp))

        SafeerTopBarIconButton(
            iconRes = R.drawable.ic_download_manager,
            contentDescription = stringResource(R.string.title_download_manager),
            onClick = onDownloadsClick
        )

        Spacer(Modifier.width(6.dp))

        SafeerTopBarIconButton(
            iconRes = R.drawable.ic_settings_account,
            contentDescription = stringResource(R.string.title_more),
            onClick = onMoreClick
        )
    }

    // Modalni dialog: Izbira vrste
    if (showTypeDialog) {
        SafeerSelectionDialog(
            title = stringResource(R.string.safeer_dialog_type),
            options = typeOptions.map { it.second },
            selectedIndex = typeOptions.indexOfFirst { it.first == selectedType }.coerceAtLeast(0),
            onSelect = { index ->
                showTypeDialog = false
                onTypeSelected(typeOptions[index].first)
            },
            onDismiss = { showTypeDialog = false }
        )
    }

    // Modalni dialog: Izbira lestvice
    if (showChartDialog) {
        SafeerSelectionDialog(
            title = stringResource(R.string.safeer_dialog_sort),
            options = chartOptions.map { it.second },
            selectedIndex = chartOptions.indexOfFirst {
                it.first == selectedChartIndex
            }.coerceAtLeast(0),
            onSelect = { index ->
                showChartDialog = false
                onChartSelected(chartOptions[index].first)
            },
            onDismiss = { showChartDialog = false }
        )
    }

    // Modalni dialog: Izbira zvrsti / kategorije
    if (showCategoryDialog) {
        val catOptions = listOf(oznakaVseZvrsti) + prednostneKategorije.map { it.title }
        val currentIndex = if (selectedCategory == null) {
            0
        } else {
            val idx = prednostneKategorije.indexOfFirst { it.title == selectedCategory.title }
            if (idx >= 0) idx + 1 else 0
        }
        SafeerSelectionDialog(
            title = stringResource(R.string.safeer_dialog_category),
            options = catOptions,
            selectedIndex = currentIndex,
            onSelect = { index ->
                showCategoryDialog = false
                if (index == 0) {
                    onCategorySelected(null)
                } else {
                    onCategorySelected(prednostneKategorije.getOrNull(index - 1))
                }
            },
            onDismiss = { showCategoryDialog = false }
        )
    }
}

@Composable
private fun SafeerTopBarChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(14.dp)
    val bgColor = if (isFocused) SafeerMint.copy(alpha = 0.22f) else Color(0x28FFFFFF)
    val borderColor = if (isFocused) SafeerMint else Color(0x33FFFFFF)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val textColor = if (isFocused) SafeerMint else Color.White

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "▾",
            color = if (isFocused) SafeerMint else Color(0xB3FFFFFF),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SafeerTopBarIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(14.dp)
    val bgColor = if (isFocused) SafeerMint.copy(alpha = 0.22f) else Color(0x28FFFFFF)
    val borderColor = if (isFocused) SafeerMint else Color(0x33FFFFFF)
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val iconTint = if (isFocused) SafeerMint else Color.White

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SafeerSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141C1E),
        title = {
            Text(
                text = title,
                color = SafeerMint,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                state = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isSelected = index == selectedIndex
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(Unit) {
                        if (index == selectedIndex.coerceAtLeast(0)) {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFocused -> SafeerMint.copy(alpha = 0.25f)
                                    isSelected -> Color(0x1FFFFFFF)
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                if (isFocused) {
                                    BorderStroke(
                                        2.dp,
                                        SafeerMint
                                    )
                                } else {
                                    BorderStroke(0.dp, Color.Transparent)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onSelect(index)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SafeerMint,
                                unselectedColor = Color(0x88FFFFFF)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option,
                            color = if (isFocused || isSelected) Color.White else Color(0xCCFFFFFF),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected ||
                                isFocused
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}
