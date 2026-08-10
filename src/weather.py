"""Récupère les conditions météo actuelles via l'API Open-Meteo (gratuite, sans clé)."""
import requests

API_URL = "https://api.open-meteo.com/v1/forecast"

# Table des codes météo WMO (code table 4677/4680) utilisée par Open-Meteo.
WEATHER_CODES = {
    0: "Ciel dégagé",
    1: "Principalement dégagé",
    2: "Partiellement nuageux",
    3: "Couvert",
    45: "Brouillard",
    48: "Brouillard givrant",
    51: "Bruine légère",
    53: "Bruine modérée",
    55: "Bruine dense",
    56: "Bruine verglaçante légère",
    57: "Bruine verglaçante dense",
    61: "Pluie légère",
    63: "Pluie modérée",
    65: "Pluie forte",
    66: "Pluie verglaçante légère",
    67: "Pluie verglaçante forte",
    71: "Neige légère",
    73: "Neige modérée",
    75: "Neige forte",
    77: "Grains de neige",
    80: "Averses légères",
    81: "Averses modérées",
    82: "Averses violentes",
    85: "Averses de neige légères",
    86: "Averses de neige fortes",
    95: "Orage",
    96: "Orage avec grêle légère",
    99: "Orage avec grêle forte",
}


def get_current_weather(lat: float, lon: float) -> dict:
    """Retourne les conditions météo actuelles pour des coordonnées données."""
    params = {
        "latitude": lat,
        "longitude": lon,
        "current": "temperature_2m,relative_humidity_2m,cloud_cover,weather_code,wind_speed_10m",
    }
    response = requests.get(API_URL, params=params, timeout=10)
    response.raise_for_status()
    current = response.json()["current"]

    return {
        "temperature_c": current.get("temperature_2m"),
        "humidity_pct": current.get("relative_humidity_2m"),
        "cloud_cover_pct": current.get("cloud_cover"),
        "wind_speed_kmh": current.get("wind_speed_10m"),
        "description": WEATHER_CODES.get(current.get("weather_code"), "Inconnu"),
    }
