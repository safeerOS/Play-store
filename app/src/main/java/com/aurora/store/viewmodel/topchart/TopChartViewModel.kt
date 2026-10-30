/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.viewmodel.topchart

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.gplayapi.helpers.contracts.TopChartsContract
import com.aurora.gplayapi.helpers.web.WebTopChartsHelper
import com.aurora.store.data.cache.SafeerStoreCache
import com.aurora.store.data.model.ViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lestvice (Najboljse brezplacne, V trendu, Z najvecjim donosom, Najboljse placljive).
 *
 * Po vzoru posterjev v Safeer Mediih: izbrana lestvica se pokaze takoj iz predpomnilnika (RAM ali disk),
 * sveza pride v ozadju. Prikaz kaze vedno samo izbrano lestvico (pozni odgovor druge je ne prepise).
 * Ce lestvice se nimamo in Trgovina ne odgovori v [CAKANJE_MS], uporabnik dobi gumb Poskusi znova -
 * nikoli neskoncnega cakanja; zahteva tece naprej in pokaze lestvico, ko pride. Ostale lestvice iste
 * vrste se nalozijo vnaprej ena za drugo, zato je naslednja izbira takojsnja.
 */
@HiltViewModel
class TopChartViewModel @Inject constructor(
    private val webTopChartsHelper: WebTopChartsHelper
) : ViewModel() {

    private data class Kljuc(val type: TopChartsContract.Type, val chart: TopChartsContract.Chart)

    private val lestvice = ConcurrentHashMap<Kljuc, StreamCluster>()
    private val vTeku = ConcurrentHashMap.newKeySet<Kljuc>()
    private val nalozeneVSeji = ConcurrentHashMap.newKeySet<Kljuc>()
    private val zaporedje = Mutex()

    @Volatile
    private var izbrana: Kljuc? = null
    private var cakanje: Job? = null
    private var vnaprej: Job? = null

    private val _state = MutableStateFlow<ViewState>(ViewState.Loading)
    val state: StateFlow<ViewState> = _state.asStateFlow()

    private val topChartsContract: TopChartsContract
        get() = webTopChartsHelper

    fun getStreamCluster(type: TopChartsContract.Type, chart: TopChartsContract.Chart) {
        val k = Kljuc(type, chart)
        izbrana = k
        cakanje?.cancel()
        val znana = lestvice[k] ?: SafeerStoreCache.beriTopChart(type, chart)?.also { lestvice[k] = it }
        if (znana != null && znana.clusterAppList.isNotEmpty()) {
            _state.value = ViewState.Success(znana)
        } else {
            _state.value = ViewState.Loading
            cakanje = viewModelScope.launch {
                delay(CAKANJE_MS)
                if (izbrana == k && lestvice[k]?.clusterAppList.isNullOrEmpty()) {
                    _state.value = ViewState.Error(null)
                }
            }
        }
        if (k !in nalozeneVSeji) viewModelScope.launch(Dispatchers.IO) { nalozi(k) }
        nalozVnaprej(type)
    }

    /** Ostale lestvice iste vrste, ena za drugo (ne obremenimo televizorja in ne Trgovine). */
    private fun nalozVnaprej(type: TopChartsContract.Type) {
        if (vnaprej?.isActive == true) return
        vnaprej = viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            for (chart in VSE_LESTVICE) {
                val k = Kljuc(type, chart)
                if (k !in nalozeneVSeji) zaporedje.withLock { nalozi(k) }
            }
        }
    }

    private suspend fun nalozi(k: Kljuc) {
        if (!vTeku.add(k)) return
        try {
            run {
                if (k in nalozeneVSeji) return
                val zacetek = SystemClock.elapsedRealtime()
                val cluster = try {
                    topChartsContract.getCluster(k.type.value, k.chart.value)
                } catch (e: Exception) {
                    Log.w(TAG, "lestvica ${k.type} ${k.chart} napaka ${e.javaClass.simpleName}")
                    if (izbrana == k && lestvice[k]?.clusterAppList.isNullOrEmpty()) {
                        cakanje?.cancel()
                        _state.value = ViewState.Error(e.message)
                    }
                    return
                }
                Log.i(TAG, "lestvica ${k.type} ${k.chart} ${cluster.clusterAppList.size} aplikacij v ${SystemClock.elapsedRealtime() - zacetek} ms")
                if (cluster.clusterAppList.isEmpty()) return
                nalozeneVSeji.add(k)
                lestvice[k] = cluster
                SafeerStoreCache.shraniTopChart(k.type, k.chart, cluster)
                if (izbrana == k) {
                    cakanje?.cancel()
                    _state.value = ViewState.Success(cluster)
                }
            }
        } finally {
            vTeku.remove(k)
        }
    }

    fun nextCluster(type: TopChartsContract.Type, chart: TopChartsContract.Chart) {
        val k = Kljuc(type, chart)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val target = lestvice[k] ?: return@launch
                if (!target.hasNext() || k !in nalozeneVSeji) return@launch
                val nova = topChartsContract.getNextStreamCluster(target.id, target.clusterNextPageUrl)
                val zdruzena = target.copy(
                    clusterNextPageUrl = nova.clusterNextPageUrl,
                    clusterAppList = (target.clusterAppList + nova.clusterAppList).distinctBy { it.packageName }
                )
                lestvice[k] = zdruzena
                if (izbrana == k) _state.value = ViewState.Success(zdruzena)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "SafeerTrgovina"
        const val CAKANJE_MS = 15_000L
        val VSE_LESTVICE = listOf(
            TopChartsContract.Chart.TOP_SELLING_FREE,
            TopChartsContract.Chart.MOVERS_SHAKERS,
            TopChartsContract.Chart.TOP_GROSSING,
            TopChartsContract.Chart.TOP_SELLING_PAID
        )
    }
}
