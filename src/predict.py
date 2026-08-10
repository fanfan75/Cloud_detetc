"""Prédit le type de nuage sur une image et affiche, en option, la météo actuelle du lieu."""
import argparse
import json
from pathlib import Path

import torch
from PIL import Image

from dataset import CLOUD_CLASSES, build_transforms
from train import build_model
from weather import get_current_weather


def load_model(checkpoint_dir: str, device):
    checkpoint_dir = Path(checkpoint_dir)
    classes = json.loads((checkpoint_dir / "classes.json").read_text())
    model = build_model(len(classes), pretrained=False)
    model.load_state_dict(torch.load(checkpoint_dir / "best_model.pt", map_location=device))
    model.to(device)
    model.eval()
    return model, classes


def predict_image(model, classes, image_path: str, device) -> dict:
    image = Image.open(image_path).convert("RGB")
    tensor = build_transforms(train=False)(image).unsqueeze(0).to(device)
    with torch.no_grad():
        probs = torch.softmax(model(tensor), dim=1)[0]
    top_idx = int(probs.argmax())
    code = classes[top_idx]
    info = CLOUD_CLASSES.get(code, {})
    return {
        "code": code,
        "name_fr": info.get("name_fr", code),
        "confidence": float(probs[top_idx]),
        "weather_fr": info.get("weather_fr", ""),
    }


def main():
    parser = argparse.ArgumentParser(description="Prédit le type de nuage sur une image")
    parser.add_argument("--image", required=True, help="Chemin vers l'image à analyser")
    parser.add_argument("--checkpoint", default="models", help="Dossier contenant best_model.pt et classes.json")
    parser.add_argument("--lat", type=float, default=None, help="Latitude pour récupérer la météo actuelle")
    parser.add_argument("--lon", type=float, default=None, help="Longitude pour récupérer la météo actuelle")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model, classes = load_model(args.checkpoint, device)
    result = predict_image(model, classes, args.image, device)

    print(f"Type de nuage détecté : {result['name_fr']} ({result['code']}) - confiance {result['confidence']:.1%}")
    print(f"Association météo typique : {result['weather_fr']}")

    if args.lat is not None and args.lon is not None:
        weather = get_current_weather(args.lat, args.lon)
        print("\nMétéo actuelle à ces coordonnées :")
        print(
            f"  {weather['description']} - {weather['temperature_c']}°C, "
            f"humidité {weather['humidity_pct']}%, couverture nuageuse {weather['cloud_cover_pct']}%, "
            f"vent {weather['wind_speed_kmh']} km/h"
        )


if __name__ == "__main__":
    main()
