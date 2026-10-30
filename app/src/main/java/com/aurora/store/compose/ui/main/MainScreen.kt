/*
 * SPDX-FileCopyrightText: 2026 Safeer OS & Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.main

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.requiresObbDir
import com.aurora.gplayapi.data.models.Category
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.store.CategoryStash
import com.aurora.store.MainViewModel
import com.aurora.store.R
import com.aurora.store.compose.composable.SafeerStoreTopBar
import com.aurora.store.compose.composable.TrackerUpdateWarningDialog
import com.aurora.store.compose.composition.LocalNetworkStatus
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.compose.ui.apps.ForYouContent
import com.aurora.store.compose.ui.apps.TopChartsContent
import com.aurora.store.compose.ui.apps.category
import com.aurora.store.compose.ui.commons.CategoryBrowseScreen
import com.aurora.store.compose.ui.commons.MoreSheet
import com.aurora.store.compose.ui.commons.NetworkScreen
import com.aurora.store.compose.ui.favourite.FavouriteScreen
import com.aurora.store.compose.ui.sheets.AppUpdateSheet
import com.aurora.store.compose.ui.updates.UpdatesScreen
import com.aurora.store.data.model.ExodusTracker
import com.aurora.store.data.model.NetworkStatus
import com.aurora.store.data.model.PermissionType
import com.aurora.store.data.model.ViewState
import com.aurora.store.data.providers.PermissionProvider.Companion.isGranted
import com.aurora.store.data.room.update.Update
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_UPDATES_WARN_TRACKERS
import com.aurora.store.viewmodel.all.UpdatesViewModel
import com.aurora.store.viewmodel.category.CategoryViewModel
import com.aurora.store.viewmodel.homestream.StreamViewModel
import com.aurora.store.viewmodel.topchart.TopChartViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    initialTab: Int = 1,
    mainViewModel: MainViewModel = hiltViewModel(),
    updatesViewModel: UpdatesViewModel = hiltViewModel(),
    streamViewModel: StreamViewModel = hiltViewModel(key = "main_stream"),
    topChartViewModel: TopChartViewModel = hiltViewModel(key = "main_topchart"),
    categoryViewModel: CategoryViewModel = hiltViewModel(key = "main_category"),
    onNavigateTo: (Destination) -> Unit = {}
) {
    val context = LocalContext.current
    val networkStatus = LocalNetworkStatus.current
    val updates by mainViewModel.updateHelper.updates.collectAsStateWithLifecycle(
        initialValue = null
    )
    val updateCount = updates?.size ?: 0
    val downloads by updatesViewModel.downloadsList.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    // 1 = Igre (privzeto za igre), 0 = Aplikacije, 2 = Posodobitve
    var selectedType by rememberSaveable {
        mutableIntStateOf(
            if (initialTab == 2) {
                2
            } else if (initialTab == 0) {
                0
            } else {
                1
            }
        )
    }
    // 0 = Priporoceno, 1 = Brezplacne, 2 = V trendu, 3 = Donosne, 4 = Placljive
    var selectedChartIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    val topBarFocusRequester = remember { FocusRequester() }

    // Nazaj: zvrst -> lestvica -> Priporoceno -> zapri Trgovino (prej je Nazaj iz zvrsti zaprl Trgovino).
    androidx.activity.compose.BackHandler(
        enabled =
        selectedCategory != null || selectedChartIndex != 0
    ) {
        if (selectedCategory != null) selectedCategory = null else selectedChartIndex = 0
    }

    // Nalaganje kategorij glede na tip (Igre ali Aplikacije)
    val categoryType = if (selectedType == 1) Category.Type.GAME else Category.Type.APPLICATION
    LaunchedEffect(categoryType) {
        categoryViewModel.getCategoryList(categoryType)
    }

    val categoryState by categoryViewModel.liveData.observeAsState()

    @Suppress("UNCHECKED_CAST")
    val categoryStash = (categoryState as? ViewState.Success<*>)?.data as? CategoryStash
    val categories = categoryStash?.get(categoryType).orEmpty()

    var showMoreSheet by remember { mutableStateOf(false) }
    var appUpdateTarget by remember { mutableStateOf<Update?>(null) }
    var trackerWarning by remember {
        mutableStateOf<Pair<Update, List<ExodusTracker>>?>(null)
    }
    val checkingJobs = remember { mutableStateMapOf<String, Job>() }

    LaunchedEffect(downloads) {
        checkingJobs.keys.toList().forEach { pkg ->
            if (downloads.any { it.packageName == pkg && !it.isFinished }) {
                checkingJobs.remove(pkg)
            }
        }
    }

    fun handleNavigation(destination: Destination) {
        when (destination) {
            is Destination.AppUpdate -> appUpdateTarget = destination.update
            else -> onNavigateTo(destination)
        }
    }

    if (networkStatus == NetworkStatus.UNAVAILABLE) {
        NetworkScreen()
        return
    }

    if (showMoreSheet) {
        MoreSheet(
            onDismiss = { showMoreSheet = false },
            onNavigateTo = { destination ->
                showMoreSheet = false
                onNavigateTo(destination)
            }
        )
    }

    appUpdateTarget?.let { app ->
        AppUpdateSheet(
            update = app,
            onDismiss = { appUpdateTarget = null },
            onNavigateTo = { destination ->
                appUpdateTarget = null
                onNavigateTo(destination)
            }
        )
    }

    // Korenski kontejner s Safeer ozadjem Pokrajina in zatemnitvijo
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                // Dolgi pritisk tipke GOR kjerkoli nas takoj vrne v zgornjo vrstico
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (keyEvent.nativeKeyEvent.isLongPress ||
                        (
                            keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.nativeKeyEvent.repeatCount > 0
                            )
                    ) {
                        topBarFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    ) {
        // Ozadje Pokrajina (slika gora iz Safeer OS)
        Image(
            painter = painterResource(R.drawable.bg_pokrajina),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Mirna Safeer zatemnitev (#0D1214) za vrhunsko berljivost
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEA0D1214))
        )

        // Scaffold brez spodnje vrstice in brez floating action buttona
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            topBar = {
                SafeerStoreTopBar(
                    selectedType = selectedType,
                    selectedChartIndex = selectedChartIndex,
                    selectedCategory = selectedCategory,
                    categories = categories,
                    updateCount = updateCount,
                    firstItemFocusRequester = topBarFocusRequester,
                    onTypeSelected = { newType ->
                        selectedType = newType
                        selectedCategory = null
                    },
                    onChartSelected = { newChart ->
                        selectedChartIndex = newChart
                        selectedCategory = null
                    },
                    onCategorySelected = { newCat ->
                        selectedCategory = newCat
                    },
                    onSearchClick = { onNavigateTo(Destination.Search) },
                    onDownloadsClick = { onNavigateTo(Destination.Downloads) },
                    onMoreClick = { showMoreSheet = true }
                )
            }
        ) { paddingValues ->
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when {
                        // 0) Safeer: nase aplikacije iz uradnih izdaj (preverjena vsota in podpis)
                        selectedType == 3 -> {
                            com.aurora.store.compose.ui.safeer.SafeerScreen()
                        }

                        // F-Droid: drugi vir odprtih aplikacij (podpisano kazalo, preverjeni paketi)
                        selectedType == 4 -> {
                            com.aurora.store.compose.ui.safeer.FDroidScreen()
                        }

                        // 1) Posodobitve
                        selectedType == 2 -> {
                            fun performUpdate(update: Update) {
                                if (update.fileList.requiresObbDir() &&
                                    !isGranted(context, PermissionType.STORAGE_MANAGER)
                                ) {
                                    checkingJobs.remove(update.packageName)
                                    onNavigateTo(
                                        Destination.PermissionRationale(
                                            setOf(PermissionType.STORAGE_MANAGER)
                                        )
                                    )
                                } else {
                                    updatesViewModel.download(update)
                                }
                            }

                            UpdatesScreen(
                                viewModel = updatesViewModel,
                                onNavigateTo = ::handleNavigation,
                                onRequestUpdate = { update ->
                                    if (!Preferences.getBoolean(
                                            context,
                                            PREFERENCE_UPDATES_WARN_TRACKERS,
                                            false
                                        )
                                    ) {
                                        performUpdate(update)
                                    } else {
                                        val job = coroutineScope.launch {
                                            val installedVc = PackageUtil.getInstalledVersionCode(
                                                context,
                                                update.packageName
                                            )
                                            val trackers = updatesViewModel.getNewTrackers(
                                                update.packageName,
                                                installedVc
                                            )
                                            if (trackers.isEmpty()) {
                                                performUpdate(update)
                                            } else {
                                                trackerWarning = update to trackers
                                            }
                                        }
                                        checkingJobs[update.packageName] = job
                                    }
                                },
                                onRequestUpdateAll = { selectedUpdates ->
                                    val needsObb = selectedUpdates.any {
                                        it.fileList.requiresObbDir()
                                    }
                                    if (needsObb &&
                                        !isGranted(context, PermissionType.STORAGE_MANAGER)
                                    ) {
                                        onNavigateTo(
                                            Destination.PermissionRationale(
                                                setOf(PermissionType.STORAGE_MANAGER)
                                            )
                                        )
                                    } else {
                                        updatesViewModel.downloadAll(selectedUpdates)
                                    }
                                },
                                onCancelUpdate = { packageName ->
                                    if (downloads.any {
                                            it.packageName == packageName && !it.isFinished
                                        }
                                    ) {
                                        checkingJobs.remove(packageName)
                                        updatesViewModel.cancelDownload(packageName)
                                    } else {
                                        checkingJobs.remove(packageName)?.cancel()
                                    }
                                },
                                onCancelAll = { updatesViewModel.cancelAll() },
                                checkingPackages = checkingJobs.keys
                            )
                        }

                        // 2) Izbrana posamezna zvrst (npr. Akcija, Strategije, Uganke...)
                        selectedCategory != null -> {
                            CategoryBrowseScreen(
                                title = selectedCategory!!.title,
                                browseUrl = selectedCategory!!.browseUrl,
                                onNavigateTo = onNavigateTo
                            )
                        }

                        // 3) Priljubljene aplikacije (❤️ Priljubljene - index 1)
                        selectedChartIndex == 1 -> {
                            FavouriteScreen(
                                onNavigateTo = onNavigateTo
                            )
                        }

                        // 4) Vse zvrsti -> Lestvice (Top Free, Trending, Top Grossing, Top Paid - index >= 2)
                        selectedChartIndex >= 2 -> {
                            TopChartsContent(
                                pageType = selectedType,
                                viewModel = topChartViewModel,
                                // Meni: 2 brezplacne, 3 v trendu, 4 donos, 5 placljive;
                                // TopChartsContent: 0 brezplacne, 1 donos, 2 v trendu, 3 placljive.
                                forcedChartIndex = when (selectedChartIndex) {
                                    3 -> 2
                                    4 -> 1
                                    5 -> 3
                                    else -> 0
                                },
                                showTabs = false,
                                onAppClick = {
                                    onNavigateTo(Destination.AppDetails(it.packageName))
                                }
                            )
                        }

                        // 4) Vse zvrsti -> Priporoceno (Za vas)
                        else -> {
                            ForYouContent(
                                pageType = selectedType,
                                viewModel = streamViewModel,
                                onAppClick = {
                                    onNavigateTo(Destination.AppDetails(it.packageName))
                                },
                                onHeaderClick = { onNavigateTo(Destination.StreamBrowse(it)) },
                                onClusterScrolled = { cluster ->
                                    streamViewModel.observeCluster(category(selectedType), cluster)
                                },
                                onScrolledToEnd = {
                                    streamViewModel.observe(
                                        category(selectedType),
                                        StreamContract.Type.HOME
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    trackerWarning?.let { (update, trackers) ->
        TrackerUpdateWarningDialog(
            trackers = trackers,
            onConfirm = {
                val pending = update
                trackerWarning = null
                if (pending.fileList.requiresObbDir() &&
                    !isGranted(context, PermissionType.STORAGE_MANAGER)
                ) {
                    checkingJobs.remove(pending.packageName)
                    onNavigateTo(
                        Destination.PermissionRationale(
                            setOf(PermissionType.STORAGE_MANAGER)
                        )
                    )
                } else {
                    updatesViewModel.download(pending)
                }
            },
            onDismiss = {
                trackerWarning = null
                checkingJobs.remove(update.packageName)
            }
        )
    }
}
