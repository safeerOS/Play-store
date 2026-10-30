/*
 * SPDX-FileCopyrightText: 2024 Aurora OSS
 * SPDX-FileCopyrightText: 2021 Rahul Kumar Patel <whyorean@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.viewmodel.subcategory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.extensions.TAG
import com.aurora.gplayapi.data.models.StreamBundle
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.contracts.CategoryStreamContract
import com.aurora.gplayapi.helpers.contracts.StreamContract
import com.aurora.gplayapi.helpers.web.WebCategoryStreamHelper
import com.aurora.store.data.cache.SafeerStoreCache
import com.aurora.store.data.model.ViewState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = CategoryStreamViewModel.Factory::class)
class CategoryStreamViewModel @AssistedInject constructor(
    @Assisted val browseUrl: String,
    private val webCategoryStreamHelper: WebCategoryStreamHelper
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(browseUrl: String): CategoryStreamViewModel
    }

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val categoryStreamContract: CategoryStreamContract
        get() = webCategoryStreamHelper

    private var streamBundle = StreamBundle.EMPTY

    @Volatile private var svezaPrva = false

    @Volatile private var nalagam = false
    private var cakanje: kotlinx.coroutines.Job? = null

    init {
        val predpomnjen = SafeerStoreCache.beriCategoryBundle(browseUrl)
        if (predpomnjen.hasCluster()) {
            streamBundle = predpomnjen
            _viewState.value = ViewState.Success(predpomnjen)
        }
        fetch()
    }

    fun fetch() {
        if (!streamBundle.hasCluster()) {
            val predpomnjen = SafeerStoreCache.beriCategoryBundle(browseUrl)
            if (predpomnjen.hasCluster()) {
                streamBundle = predpomnjen
                _viewState.value = ViewState.Success(predpomnjen)
            }
        }
        if (!streamBundle.hasCluster()) {
            _viewState.value = ViewState.Loading
            cakanje?.cancel()
            cakanje = viewModelScope.launch {
                kotlinx.coroutines.delay(15_000L)
                if (!streamBundle.hasCluster()) _viewState.value = ViewState.Error(null)
            }
        }
        if (nalagam) return
        nalagam = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!svezaPrva || streamBundle.hasNext()) {
                    // Prva stran zvrsti vedno sveza iz Trgovine (predpomnjena je samo za takojsen prikaz);
                    // pozen odgovor ne zavrzemo - pokaze se, ko pride.
                    val prva = !svezaPrva
                    val newBundle = if (prva) {
                        categoryStreamContract.fetch(browseUrl)
                    } else {
                        categoryStreamContract.nextStreamBundle(
                            streamBundle.id,
                            StreamContract.Category.NONE,
                            streamBundle.streamNextPageUrl
                        )
                    }

                    if (newBundle.hasCluster()) {
                        streamBundle = if (prva) {
                            newBundle
                        } else {
                            streamBundle.copy(
                                streamClusters =
                                streamBundle.streamClusters + newBundle.streamClusters,
                                streamNextPageUrl = newBundle.streamNextPageUrl
                            )
                        }
                        svezaPrva = true
                        cakanje?.cancel()
                        SafeerStoreCache.shraniCategoryBundle(browseUrl, streamBundle)
                        _viewState.value = ViewState.Success(streamBundle)
                    }
                } else {
                    Log.i(TAG, "End of Bundle")
                }
            } catch (e: Exception) {
                if (!streamBundle.hasCluster()) {
                    cakanje?.cancel()
                    _viewState.value = ViewState.Error(e.message)
                }
            } finally {
                nalagam = false
            }
        }
    }

    fun fetchNextCluster(streamCluster: StreamCluster) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (streamCluster.hasNext()) {
                    val newCluster = categoryStreamContract.nextStreamCluster(
                        streamCluster.id,
                        streamCluster.clusterNextPageUrl
                    )
                    val mergedCluster = streamCluster.copy(
                        clusterNextPageUrl = newCluster.clusterNextPageUrl,
                        clusterAppList =
                        streamCluster.clusterAppList + newCluster.clusterAppList
                    )

                    val newClusters = streamBundle.streamClusters.toMutableMap().apply {
                        this[streamCluster.id] = mergedCluster
                    }
                    streamBundle = streamBundle.copy(streamClusters = newClusters)
                    _viewState.value = ViewState.Success(streamBundle)
                } else {
                    Log.i(TAG, "End of cluster")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch next cluster", e)
            }
        }
    }
}
