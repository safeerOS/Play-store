package com.aurora.store.compose.ui.safeer

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.store.data.safeer.SafeerIzdaje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Meta = Color(0xFF7EDCAB)

class SafeerViewModel : ViewModel() {

    data class Vrstica(val paket: SafeerIzdaje.Paket, val namesceno: String?) {
        val posodobitev: Boolean get() = namesceno != null &&
            SafeerIzdaje.novejsa(paket.verzija, namesceno)
        val potrebno: Boolean get() = namesceno == null || posodobitev
    }

    sealed interface Stanje {
        data object Nalagam : Stanje
        data class Napaka(val besedilo: String) : Stanje
        data class Seznam(val vrstice: List<Vrstica>) : Stanje
    }

    private val _stanje = MutableStateFlow<Stanje>(Stanje.Nalagam)
    val stanje: StateFlow<Stanje> = _stanje.asStateFlow()
    private val vTeku = mutableSetOf<String>()

    fun nalozi(context: Context, sveze: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paketi = SafeerIzdaje.nalozi(context, SafeerIzdaje.naprava(context), sveze)
                _stanje.value =
                    Stanje.Seznam(
                        paketi.map {
                            Vrstica(it, SafeerIzdaje.namescena(context, it.paket))
                        }
                    )
            } catch (e: Exception) {
                if (_stanje.value !is Stanje.Seznam) {
                    _stanje.value =
                        Stanje.Napaka(
                            "Izdaje Safeer niso dosegljive. Preveri povezavo in poskusi znova."
                        )
                }
            }
        }
    }

    fun namesti(context: Context, v: Vrstica) {
        val p = v.paket
        synchronized(vTeku) { if (!vTeku.add(p.paket)) return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SafeerIzdaje.sporoci(p.paket, "Prenašam 0 %")
                val f = SafeerIzdaje.prenesiInPreveri(context, p) {
                    SafeerIzdaje.sporoci(p.paket, "Prenašam $it %")
                }
                SafeerIzdaje.sporoci(p.paket, "Preverjeno (vsota in Safeer podpis), nameščam …")
                SafeerIzdaje.namesti(context, f, p.paket)
            } catch (e: SecurityException) {
                SafeerIzdaje.sporoci(p.paket, "Zavrnjeno: ${e.message}")
            } catch (e: Exception) {
                SafeerIzdaje.sporoci(p.paket, "Prenos ni uspel, poskusi znova")
            } finally {
                synchronized(vTeku) { vTeku.remove(p.paket) }
            }
        }
    }
}

@Composable
fun SafeerScreen(vm: SafeerViewModel = viewModel()) {
    val context = LocalContext.current.applicationContext
    val stanje by vm.stanje.collectAsStateWithLifecycle()
    val sporocila by SafeerIzdaje.sporocila.collectAsStateWithLifecycle()

    // Ob odprtju in po vsaki koncani namestitvi osvezimo namescene razlicice.
    val namescenih = sporocila.values.count { it == "Nameščeno" }
    LaunchedEffect(namescenih) { vm.nalozi(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(
                    "Safeer aplikacije",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Uradne izdaje Safeer. Pred namestitvijo preverimo kontrolno vsoto in Safeer podpisni ključ.",
                    color = Color(0xB3FFFFFF),
                    fontSize = 13.sp
                )
            }
        }
        when (val s = stanje) {
            is SafeerViewModel.Stanje.Nalagam -> item {
                Text("Nalagam izdaje …", color = Color(0xB3FFFFFF), fontSize = 15.sp)
            }
            is SafeerViewModel.Stanje.Napaka -> item {
                Kartica(naslov = s.besedilo, opis = "", desno = "Poskusi znova") {
                    vm.nalozi(context, sveze = true)
                }
            }
            is SafeerViewModel.Stanje.Seznam -> {
                if (s.vrstice.isEmpty()) {
                    item {
                        Text(
                            "Za to napravo ni Safeer izdaj.",
                            color = Color(0xB3FFFFFF),
                            fontSize = 15.sp
                        )
                    }
                }
                items(s.vrstice, key = { it.paket.paket }) { v ->
                    val opis = buildString {
                        append(
                            if (v.namesceno !=
                                null
                            ) {
                                "Nameščeno ${v.namesceno}"
                            } else {
                                "Ni nameščeno"
                            }
                        )
                        append("  ·  Na voljo ${v.paket.verzija}")
                        if (v.paket.velikost >
                            0
                        ) {
                            append("  ·  ${v.paket.velikost / (1024 * 1024)} MB")
                        }
                    }
                    val desno = when {
                        v.posodobitev -> "Posodobi"
                        v.namesceno == null -> "Namesti"
                        else -> "Najnovejša"
                    }
                    Kartica(
                        naslov = v.paket.ime,
                        opis = opis,
                        stanje = sporocila[v.paket.paket],
                        desno = desno,
                        poudarek = v.potrebno
                    ) { if (v.potrebno) vm.namesti(context, v) }
                }
            }
        }
    }
}

@Composable
private fun Kartica(
    naslov: String,
    opis: String,
    desno: String,
    stanje: String? = null,
    poudarek: Boolean = true,
    onClick: () -> Unit
) {
    val interakcija = remember { MutableInteractionSource() }
    val fokus by interakcija.collectIsFocusedAsState()
    val oblika = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(oblika)
            .background(if (fokus) Meta.copy(alpha = 0.22f) else Color(0x28FFFFFF))
            .border(
                BorderStroke(if (fokus) 2.dp else 1.dp, if (fokus) Meta else Color(0x33FFFFFF)),
                oblika
            )
            .clickable(interactionSource = interakcija, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                naslov,
                color = if (fokus) Meta else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (opis.isNotEmpty()) Text(opis, color = Color(0xCCFFFFFF), fontSize = 13.sp)
            if (!stanje.isNullOrEmpty()) Text(stanje, color = Meta, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            desno,
            color = if (poudarek) Meta else Color(0x99FFFFFF),
            fontSize = 15.sp,
            fontWeight = if (poudarek) FontWeight.Bold else FontWeight.Normal
        )
    }
}
