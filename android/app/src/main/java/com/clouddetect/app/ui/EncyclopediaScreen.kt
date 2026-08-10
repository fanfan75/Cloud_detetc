package com.clouddetect.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clouddetect.app.data.CLOUD_DATABASE
import com.clouddetect.app.data.CloudInfo

@Composable
fun EncyclopediaScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(CLOUD_DATABASE) { cloud -> CloudCard(cloud) }
    }
}

@Composable
fun CloudCard(cloud: CloudInfo) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("${cloud.nameFr} (${cloud.code})", style = MaterialTheme.typography.titleMedium)
            Text(cloud.famille, style = MaterialTheme.typography.labelMedium)
            Text(cloud.description, modifier = Modifier.padding(top = 8.dp))
            Text("Altitude : ${cloud.altitude}", modifier = Modifier.padding(top = 8.dp))
            Text("Forme : ${cloud.forme}")
            Text("Couleur : ${cloud.couleur}")
            Text(
                "Météo associée : ${cloud.meteoAssociee}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
