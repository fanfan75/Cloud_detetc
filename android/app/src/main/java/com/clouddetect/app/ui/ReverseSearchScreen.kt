package com.clouddetect.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clouddetect.app.data.CLOUD_DATABASE

// Ces libellés sont comparés avec `contains` sur le champ `famille` : ils doivent donc rester
// des préfixes valides des familles réelles (ex. « Particularité » couvre
// « Particularité supplémentaire »).
private val FAMILLES = listOf(
    "Nuages hauts",
    "Nuages moyens",
    "Nuages bas",
    "Développement vertical",
    "Particularité",
    "Nuage spécial",
)
private val COULEURS = listOf("Blanc", "Gris", "Noir", "Irisations")

@Composable
fun ReverseSearchScreen() {
    var query by remember { mutableStateOf("") }
    var selectedFamilles by remember { mutableStateOf(setOf<String>()) }
    var selectedCouleurs by remember { mutableStateOf(setOf<String>()) }

    val results = CLOUD_DATABASE.filter { cloud ->
        val matchesQuery = query.isBlank() ||
            cloud.nameFr.contains(query, ignoreCase = true) ||
            cloud.nameLatin.contains(query, ignoreCase = true) ||
            cloud.forme.contains(query, ignoreCase = true) ||
            cloud.composition.contains(query, ignoreCase = true) ||
            cloud.precipitations.contains(query, ignoreCase = true) ||
            cloud.description.contains(query, ignoreCase = true) ||
            cloud.especes.any { it.nom.contains(query, ignoreCase = true) }

        val matchesFamille = selectedFamilles.isEmpty() ||
            selectedFamilles.any { cloud.famille.contains(it, ignoreCase = true) }

        val matchesCouleur = selectedCouleurs.isEmpty() ||
            selectedCouleurs.any { cloud.couleur.contains(it, ignoreCase = true) }

        matchesQuery && matchesFamille && matchesCouleur
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Décris ce que tu observes pour retrouver le nuage", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Forme, aspect… (ex : \"fibreux\", \"chou-fleur\")") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Text("Altitude / famille", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        LazyRow(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(FAMILLES) { famille ->
                FilterChip(
                    selected = famille in selectedFamilles,
                    onClick = {
                        selectedFamilles = if (famille in selectedFamilles) {
                            selectedFamilles - famille
                        } else {
                            selectedFamilles + famille
                        }
                    },
                    label = { Text(famille) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Text("Couleur dominante", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        LazyRow(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(COULEURS) { couleur ->
                FilterChip(
                    selected = couleur in selectedCouleurs,
                    onClick = {
                        selectedCouleurs = if (couleur in selectedCouleurs) {
                            selectedCouleurs - couleur
                        } else {
                            selectedCouleurs + couleur
                        }
                    },
                    label = { Text(couleur) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        Text("${results.size} résultat(s)", style = MaterialTheme.typography.labelMedium)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results) { cloud -> CloudCard(cloud) }
        }
    }
}
