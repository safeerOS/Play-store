package com.aurora.store.compose.ui.safeer

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aurora.store.compose.composable.rememberSafeerMreza
import com.aurora.store.data.safeer.FDroidVir
import com.aurora.store.data.safeer.SafeerIzdaje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Meta = Color(0xFF7EDCAB)

class FDroidViewModel : ViewModel() {

    sealed interface Stanje {
        data class Nalagam(val odstotek: Int) : Stanje
        data class Napaka(val besedilo: String) : Stanje
        data class Pripravljen(val katalog: FDroidVir.Katalog) : Stanje
    }

    private val _stanje = MutableStateFlow<Stanje>(Stanje.Nalagam(0))
    val stanje: StateFlow<Stanje> = _stanje.asStateFlow()
    private var zacetek = false
    private val vTeku = mutableSetOf<String>()

    fun nalozi(context: Context, sveze: Boolean = false) {
        if (zacetek && !sveze) return
        zacetek = true
        viewModelScope.launch(Dispatchers.IO) {
            // Shranjen katalog takoj (po vzoru posterjev), nov z F-Droida v ozadju.
            FDroidVir.shranjen(context)?.let { _stanje.value = Stanje.Pripravljen(it) }
            try {
                val k = FDroidVir.osvezi(context) { o ->
                    if (_stanje.value !is Stanje.Pripravljen) _stanje.value = Stanje.Nalagam(o)
                }
                _stanje.value = Stanje.Pripravljen(k)
            } catch (e: SecurityException) {
                if (_stanje.value !is Stanje.Pripravljen) {
                    _stanje.value = Stanje.Napaka("Kazalo F-Droid ni prestalo preverbe podpisa.")
                }
            } catch (e: Exception) {
                if (_stanje.value !is Stanje.Pripravljen) {
                    _stanje.value =
                        Stanje.Napaka("F-Droid ni dosegljiv. Preveri povezavo in poskusi znova.")
                }
            }
        }
    }

    fun namesti(context: Context, a: FDroidVir.Aplikacija) {
        synchronized(vTeku) { if (!vTeku.add(a.paket)) return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SafeerIzdaje.sporoci(a.paket, "Prenašam 0 %")
                val f = FDroidVir.prenesiInPreveri(context, a) {
                    SafeerIzdaje.sporoci(a.paket, "Prenašam $it %")
                }
                SafeerIzdaje.sporoci(a.paket, "Preverjeno (vsota in podpisnik F-Droid), nameščam …")
                SafeerIzdaje.namesti(context, f, a.paket)
            } catch (e: SecurityException) {
                SafeerIzdaje.sporoci(a.paket, "Zavrnjeno: ${e.message}")
            } catch (e: Exception) {
                SafeerIzdaje.sporoci(a.paket, "Prenos ni uspel, poskusi znova")
            } finally {
                synchronized(vTeku) { vTeku.remove(a.paket) }
            }
        }
    }
}

private const val VSE = ""

@Composable
fun FDroidScreen(vm: FDroidViewModel = viewModel()) {
    val context = LocalContext.current.applicationContext
    val stanje by vm.stanje.collectAsStateWithLifecycle()
    val sporocila by SafeerIzdaje.sporocila.collectAsStateWithLifecycle()
    var zvrst by rememberSaveable { mutableStateOf(VSE) }
    var izbrana by remember { mutableStateOf<FDroidVir.Aplikacija?>(null) }
    LaunchedEffect(Unit) { vm.nalozi(context) }

    when (val s = stanje) {
        is FDroidViewModel.Stanje.Nalagam -> Sredina(
            if (s.odstotek > 0) {
                "Prenašam in preverjam katalog F-Droid … ${s.odstotek} %"
            } else {
                "Nalagam katalog F-Droid …"
            }
        )
        is FDroidViewModel.Stanje.Napaka -> Column(Modifier.padding(16.dp)) {
            Text(s.besedilo, color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Gumb("Poskusi znova", true) { vm.nalozi(context, sveze = true) }
        }
        is FDroidViewModel.Stanje.Pripravljen -> {
            val k = s.katalog
            val stevila =
                remember(k) { k.aplikacije.flatMap { it.zvrsti }.groupingBy { it }.eachCount() }
            val zvrsti =
                remember(k) {
                    k.zvrsti.filterKeys { (stevila[it] ?: 0) > 0 }.entries.sortedBy { it.value }
                }
            val vidne = remember(k, zvrst) {
                if (zvrst == VSE) k.aplikacije else k.aplikacije.filter { zvrst in it.zvrsti }
            }
            val mreza = rememberSafeerMreza()
            val vrstice = remember(vidne, mreza.stolpci) { vidne.chunked(mreza.stolpci) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "F-Droid",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Odprte aplikacije brez Googla (${k.aplikacije.size} za to napravo). " +
                                "Kazalo in vsak paket preverimo s podpisom F-Droid.",
                            color = Color(0xB3FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Gumb("Vse", zvrst == VSE) { zvrst = VSE } }
                        items(zvrsti, key = { it.key }) { (id, ime) ->
                            Gumb("$ime · ${stevila[id] ?: 0}", zvrst == id) { zvrst = id }
                        }
                    }
                }
                items(vrstice.size, key = { "fd_$zvrst$it" }) { i ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (a in vrstice[i]) {
                            Kartica(a, mreza.sirina) { izbrana = a }
                        }
                    }
                }
            }
        }
    }

    izbrana?.let { a ->
        val namescena = remember(a, sporocila) { SafeerIzdaje.namescena(context, a.paket) }
        val potrebno = namescena == null || SafeerIzdaje.novejsa(a.verzija, namescena)
        AlertDialog(
            onDismissRequest = { izbrana = null },
            containerColor = Color(0xFF141C1E),
            title = { Text(a.ime, color = Meta, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(a.opis, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (namescena != null) "Nameščeno $namescena  ·  " else "") +
                            "Na voljo ${a.verzija}  ·  ${a.velikost / (1024 * 1024)} MB",
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp
                    )
                    sporocila[a.paket]?.let { Text(it, color = Meta, fontSize = 13.sp) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gumb(
                            when {
                                namescena == null -> "Namesti"
                                potrebno -> "Posodobi"
                                else -> "Najnovejša"
                            },
                            potrebno
                        ) { if (potrebno) vm.namesti(context, a) }
                        Gumb("Zapri", false) { izbrana = null }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun Sredina(besedilo: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(besedilo, color = Color(0xCCFFFFFF), fontSize = 15.sp)
    }
}

@Composable
private fun Gumb(besedilo: String, poudarek: Boolean, onClick: () -> Unit) {
    val interakcija = remember { MutableInteractionSource() }
    val fokus by interakcija.collectIsFocusedAsState()
    val oblika = RoundedCornerShape(14.dp)
    Text(
        besedilo,
        modifier = Modifier
            .clip(oblika)
            .background(
                if (fokus) {
                    Meta.copy(alpha = 0.22f)
                } else if (poudarek) {
                    Color(0x337EDCAB)
                } else {
                    Color(0x28FFFFFF)
                }
            )
            .border(
                BorderStroke(if (fokus) 2.dp else 1.dp, if (fokus) Meta else Color(0x33FFFFFF)),
                oblika
            )
            .clickable(interactionSource = interakcija, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = if (fokus) Meta else Color.White,
        fontSize = 14.sp,
        fontWeight = if (fokus || poudarek) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1
    )
}

@Composable
private fun Kartica(
    a: FDroidVir.Aplikacija,
    sirina: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interakcija = remember { MutableInteractionSource() }
    val fokus by interakcija.collectIsFocusedAsState()
    val oblika = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(sirina)
            .clip(oblika)
            .background(if (fokus) Color(0x332DD4BF) else Color(0x28FFFFFF))
            .border(
                BorderStroke(if (fokus) 2.dp else 1.dp, if (fokus) Meta else Color(0x22FFFFFF)),
                oblika
            )
            .clickable(interactionSource = interakcija, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33000000)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = FDroidVir.ikonaUrl(a),
                contentDescription = a.ime,
                modifier = Modifier.size(sirina - 24.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            a.ime,
            modifier = Modifier.fillMaxWidth(),
            color = if (fokus) Meta else Color.White,
            fontSize = 12.sp,
            fontWeight = if (fokus) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}
