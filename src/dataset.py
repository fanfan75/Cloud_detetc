"""Chargement du dataset CCSN (un sous-dossier d'images par classe de nuage)."""
from pathlib import Path

import torch
from torch.utils.data import random_split
from torchvision import datasets, transforms

IMG_SIZE = 224

MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]

# Classes du dataset CCSN et association typique avec le temps observé.
CLOUD_CLASSES = {
    "Ci": {"name_fr": "Cirrus", "weather_fr": "Ciel généralement clair ; annonce parfois un changement de temps sous 24-48h."},
    "Cs": {"name_fr": "Cirrostratus", "weather_fr": "Voile fin avec halo autour du soleil/lune ; précède souvent une perturbation pluvieuse sous 12-24h."},
    "Cc": {"name_fr": "Cirrocumulus", "weather_fr": "Ciel moutonné en haute altitude ; temps généralement calme, parfois signe d'instabilité à venir."},
    "Ac": {"name_fr": "Altocumulus", "weather_fr": "Nuages moutonnés d'altitude moyenne ; beau temps, mais peut annoncer des orages en fin de journée."},
    "As": {"name_fr": "Altostratus", "weather_fr": "Voile gris uniforme ; précède souvent des précipitations continues (pluie ou neige)."},
    "Cu": {"name_fr": "Cumulus", "weather_fr": "Nuages de beau temps ; s'ils grossissent (cumulus congestus), risque d'averses ou d'orages."},
    "Cb": {"name_fr": "Cumulonimbus", "weather_fr": "Nuage d'orage : fortes pluies, grêle, foudre, rafales possibles."},
    "Ns": {"name_fr": "Nimbostratus", "weather_fr": "Couche grise épaisse ; pluie ou neige continue et durable."},
    "Sc": {"name_fr": "Stratocumulus", "weather_fr": "Nuages bas en bancs ; temps sec le plus souvent, parfois bruine légère."},
    "St": {"name_fr": "Stratus", "weather_fr": "Couche basse et uniforme ; bruine, brouillard, ciel maussade."},
    "Ct": {"name_fr": "Contrail", "weather_fr": "Traînée de condensation d'avion ; indicateur d'humidité en haute altitude, pas de lien météo direct."},
}


def build_transforms(train: bool):
    if train:
        return transforms.Compose([
            transforms.Resize((IMG_SIZE, IMG_SIZE)),
            transforms.RandomHorizontalFlip(),
            transforms.RandomRotation(10),
            transforms.ColorJitter(brightness=0.2, contrast=0.2),
            transforms.ToTensor(),
            transforms.Normalize(mean=MEAN, std=STD),
        ])
    return transforms.Compose([
        transforms.Resize((IMG_SIZE, IMG_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize(mean=MEAN, std=STD),
    ])


def load_dataset(data_dir: str, val_split: float = 0.2, seed: int = 42):
    """Charge un dataset structuré en un sous-dossier par classe et le sépare en train/val."""
    data_dir = Path(data_dir)
    full = datasets.ImageFolder(data_dir, transform=build_transforms(train=True))
    n_val = int(len(full) * val_split)
    n_train = len(full) - n_val
    generator = torch.Generator().manual_seed(seed)
    train_set, val_set = random_split(full, [n_train, n_val], generator=generator)

    # Le split de validation doit utiliser les transformations sans augmentation.
    val_set.dataset = datasets.ImageFolder(data_dir, transform=build_transforms(train=False))

    return train_set, val_set, full.classes
