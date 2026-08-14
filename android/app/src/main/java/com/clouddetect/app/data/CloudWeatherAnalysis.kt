package com.clouddetect.app.data

import com.clouddetect.app.weather.CurrentWeather
import kotlin.math.roundToInt

/**
 * Croise le type de nuage observé avec les conditions météo mesurées à la position de
 * l'observateur, pour expliquer ce que l'association des deux suggère.
 *
 * Les règles ci-dessous sont des repères d'observation météo classiques, pas une
 * prévision numérique : elles décrivent des tendances, avec les réserves d'usage.
 */
fun analyserNuageEtMeteo(cloud: CloudInfo, weather: CurrentWeather?): List<String> {
    if (weather == null) {
        return listOf(
            "Météo locale indisponible : l'analyse se limite au type de nuage observé. " +
                "Active la localisation et une connexion pour croiser les deux."
        )
    }

    val points = mutableListOf<String>()

    points += coherenceCouverture(cloud, weather)
    tendanceHumidite(cloud, weather)?.let { points += it }
    tendancePression(cloud, weather)?.let { points += it }
    precipitationsEnCours(cloud, weather)?.let { points += it }
    ventEtTemperature(cloud, weather)?.let { points += it }
    points += conclusion(cloud, weather)

    return points
}

/** Le type détecté est-il cohérent avec la couverture nuageuse mesurée ? */
private fun coherenceCouverture(cloud: CloudInfo, weather: CurrentWeather): String {
    val couverture = weather.cloudCoverPct
    if (couverture < 0) return "Couverture nuageuse non communiquée par la station la plus proche."

    val attendu = when (cloud.code) {
        "St", "Ns", "As" -> 80..100
        "Sc", "Cs" -> 50..100
        "Ac", "Cc", "Cb" -> 25..90
        "Cu" -> 10..60
        "Ci", "Ct" -> 0..50
        else -> 0..100
    }

    return when {
        couverture in attendu ->
            "Cohérent : la couverture mesurée ($couverture %) correspond bien à ce qu'on attend " +
                "avec des ${cloud.nameFr}."
        couverture < attendu.first ->
            "À nuancer : le ciel n'est couvert qu'à $couverture %, moins que ce qu'implique " +
                "généralement un ciel de ${cloud.nameFr}. Le nuage visé est peut-être isolé, " +
                "ou la station de référence est un peu loin."
        else ->
            "À nuancer : le ciel est couvert à $couverture %, davantage que ce qu'implique " +
                "généralement un ciel de ${cloud.nameFr}. Plusieurs couches se superposent " +
                "peut-être au-dessus de toi."
    }
}

private fun tendanceHumidite(cloud: CloudInfo, weather: CurrentWeather): String? {
    val humidite = weather.humidityPct
    if (humidite < 0) return null

    return when {
        humidite >= 85 && cloud.code in setOf("Cs", "As", "Ns", "St") ->
            "Air très humide ($humidite %) sous un nuage de couche : conditions réunies pour " +
                "des précipitations continues ou une bruine persistante."
        humidite >= 85 && cloud.code in setOf("Cu", "Cb") ->
            "Air très humide ($humidite %) avec un nuage convectif : carburant idéal pour des " +
                "averses, et pour que le nuage continue de grossir."
        humidite in 60..84 ->
            "Humidité modérée ($humidite %) : l'atmosphère contient de quoi entretenir les nuages " +
                "sans forcément produire de précipitations au sol."
        humidite < 40 && cloud.code in setOf("Ci", "Ct", "Cc") ->
            "Air sec près du sol ($humidite %) alors que les nuages sont en altitude : " +
                "typique d'un temps calme, l'humidité restant cantonnée en haute troposphère."
        humidite < 40 ->
            "Air sec ($humidite %) : les précipitations éventuelles risquent de s'évaporer avant " +
                "d'atteindre le sol (virga)."
        else -> null
    }
}

private fun tendancePression(cloud: CloudInfo, weather: CurrentWeather): String? {
    val pression = weather.pressureHpa
    if (pression.isNaN()) return null
    val arrondie = pression.roundToInt()

    return when {
        pression < 1000 ->
            "Pression basse ($arrondie hPa) : régime dépressionnaire, ce qui va dans le sens " +
                "d'un temps perturbé ou en voie de le devenir."
        pression < 1013 ->
            "Pression légèrement sous la normale ($arrondie hPa) : temps changeant, une " +
                "perturbation peut circuler dans les parages."
        pression > 1025 ->
            "Pression élevée ($arrondie hPa) : anticyclone bien installé, qui limite le " +
                "développement vertical des nuages et favorise le temps sec."
        else ->
            "Pression proche de la normale ($arrondie hPa) : pas de signal fort dans un sens " +
                "ou dans l'autre."
    }
}

private fun precipitationsEnCours(cloud: CloudInfo, weather: CurrentWeather): String? {
    val pluie = weather.precipitationMm
    val descriptionCiel = weather.description

    return when {
        pluie > 2.0 ->
            "Précipitations soutenues en cours (${formatMm(pluie)} mm sur la dernière heure, " +
                "$descriptionCiel) : le nuage observé participe activement au temps du moment."
        pluie > 0.0 ->
            "Précipitations faibles en cours (${formatMm(pluie)} mm, $descriptionCiel)."
        weather.weatherCode in setOf(95, 96, 99) ->
            "Orage signalé dans le secteur : à confirmer visuellement, mais la prudence " +
                "s'impose si tu observes dehors."
        cloud.code == "Cb" ->
            "Aucune précipitation mesurée pour l'instant, mais un cumulonimbus peut se déclencher " +
                "en moins d'une heure : surveille son évolution."
        else -> null
    }
}

private fun ventEtTemperature(cloud: CloudInfo, weather: CurrentWeather): String? {
    val vent = weather.windSpeedKmh
    val temperature = weather.temperatureC
    if (vent.isNaN() || temperature.isNaN()) return null

    val ventArrondi = vent.roundToInt()
    val tempArrondie = temperature.roundToInt()

    return when {
        vent >= 40 ->
            "Vent fort ($ventArrondi km/h) : les nuages défilent vite, la situation peut changer " +
                "d'aspect en quelques dizaines de minutes."
        temperature >= 25 && cloud.code in setOf("Cu", "Cb", "Ac") ->
            "Il fait $tempArrondie °C avec un vent de $ventArrondi km/h : la chaleur alimente la " +
                "convection, les nuages bourgeonnants ont tendance à grossir l'après-midi."
        temperature <= 2 ->
            "Température de $tempArrondie °C : d'éventuelles précipitations pourraient tomber " +
                "sous forme de neige ou de pluie verglaçante."
        else ->
            "Il fait $tempArrondie °C avec un vent de $ventArrondi km/h."
    }
}

private fun conclusion(cloud: CloudInfo, weather: CurrentWeather): String {
    val humidite = weather.humidityPct
    val pression = weather.pressureHpa
    val pluie = weather.precipitationMm

    val degradation = (humidite >= 80) || (!pression.isNaN() && pression < 1008) || pluie > 0.0
    val amelioration = (humidite in 0..55) && (!pression.isNaN() && pression > 1020)

    return when (cloud.code) {
        "Ci", "Cs" -> if (degradation) {
            "Synthèse : nuages élevés annonciateurs, sur un fond déjà humide ou dépressionnaire. " +
                "Une perturbation est probablement en approche dans les 12 à 24 heures."
        } else {
            "Synthèse : nuages élevés sans signal de dégradation dans les mesures au sol. " +
                "Le temps devrait rester correct à court terme, mais surveille si le voile s'épaissit."
        }
        "Cc", "Ac" -> if (degradation) {
            "Synthèse : nuages d'altitude moyenne dans une atmosphère humide. Instabilité " +
                "possible en cours de journée, averses ou orages ne sont pas à exclure."
        } else {
            "Synthèse : ciel moutonné dans une atmosphère plutôt stable. Temps variable mais " +
                "sans dégradation marquée attendue."
        }
        "As", "Ns" -> "Synthèse : couche épaisse caractéristique d'une perturbation organisée. " +
            "Précipitations durables probables ou déjà en cours ; l'éclaircie n'est pas pour tout de suite."
        "St", "Sc" -> if (amelioration) {
            "Synthèse : couche basse sous un régime anticyclonique. Souvent des grisailles " +
                "matinales qui se dissipent avec le réchauffement de la journée."
        } else {
            "Synthèse : ciel bas et gris, temps maussade sans forte précipitation attendue. " +
                "Bruine et visibilité réduite possibles."
        }
        "Cu" -> if (degradation) {
            "Synthèse : cumulus dans un air humide, à surveiller. S'ils continuent de bourgeonner " +
                "verticalement, ils peuvent évoluer vers des averses en fin de journée."
        } else {
            "Synthèse : cumulus de beau temps dans une atmosphère stable. Signe classique d'une " +
                "belle journée, sans risque particulier."
        }
        "Cb" -> "Synthèse : cumulonimbus, le nuage le plus actif de la classification. " +
            "Foudre, rafales et fortes précipitations sont possibles à courte échéance. " +
            "Mets-toi à l'abri si le nuage se rapproche."
        "Ct" -> if (humidite >= 70) {
            "Synthèse : les traînées d'avion persistent, signe d'une haute troposphère humide. " +
                "Cela accompagne souvent l'arrivée d'un système nuageux dans les 24 à 48 heures."
        } else {
            "Synthèse : traînées de condensation dans un air d'altitude sec ; elles devraient se " +
                "dissiper vite. Pas de signal météo particulier."
        }
        else -> "Synthèse : observation à recouper avec l'évolution du ciel dans les prochaines heures."
    }
}

private fun formatMm(value: Double): String {
    val arrondi = (value * 10).roundToInt() / 10.0
    return arrondi.toString()
}
