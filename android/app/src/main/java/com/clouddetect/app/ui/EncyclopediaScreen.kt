package com.clouddetect.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clouddetect.app.data.CLOUD_DATABASE
import com.clouddetect.app.data.CloudInfo

@Composable
fun EncyclopediaScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(CLOUD_DATABASE) { cloud -> CloudCard(cloud) }
    }
}

@Composable
fun CloudCard(cloud: CloudInfo) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        if (cloud.images.isNotEmpty()) {
            LazyRow {
                items(cloud.images) { imageRes ->
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "Photo d'un ciel avec des nuages de type ${cloud.nameFr}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(260.dp)
                            .height(180.dp)
                            .padding(end = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text("${cloud.nameFr} (${cloud.code})", style = MaterialTheme.typography.titleMedium)
            Text(cloud.famille, style = MaterialTheme.typography.labelMedium)
            if (!cloud.detectable) {
                Text(
                    "Non reconnu par l'appareil photo : à repérer à l'œil.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(cloud.description, modifier = Modifier.padding(top = 8.dp))
            Text("Altitude : ${cloud.altitude}", modifier = Modifier.padding(top = 8.dp))
            Text("Forme : ${cloud.forme}")
            Text("Couleur : ${cloud.couleur}")
            Text("Composition : ${cloud.composition}")
            Text("Précipitations : ${cloud.precipitations}")
            Text(
                "Météo associée : ${cloud.meteoAssociee}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (cloud.especes.isNotEmpty()) {
                Text(
                    "Espèces et variétés",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                cloud.especes.forEach { espece ->
                    Text(
                        "${espece.nom} — ${espece.description}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
