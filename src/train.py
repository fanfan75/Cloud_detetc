"""Entraîne un classifieur de type de nuage par transfer learning (ResNet18)."""
import argparse
import json
from pathlib import Path

import torch
from torch import nn, optim
from torch.utils.data import DataLoader
from torchvision import models

from dataset import load_dataset


def build_model(num_classes: int, pretrained: bool = True) -> nn.Module:
    weights = models.ResNet18_Weights.IMAGENET1K_V1 if pretrained else None
    model = models.resnet18(weights=weights)
    model.fc = nn.Linear(model.fc.in_features, num_classes)
    return model


def train(args):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_set, val_set, classes = load_dataset(args.data_dir, val_split=args.val_split)
    train_loader = DataLoader(train_set, batch_size=args.batch_size, shuffle=True, num_workers=2)
    val_loader = DataLoader(val_set, batch_size=args.batch_size, shuffle=False, num_workers=2)

    model = build_model(len(classes), pretrained=args.pretrained).to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=args.lr)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    best_acc = -1.0

    for epoch in range(args.epochs):
        model.train()
        running_loss = 0.0
        for images, labels in train_loader:
            images, labels = images.to(device), labels.to(device)
            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            running_loss += loss.item() * images.size(0)
        train_loss = running_loss / len(train_set)

        model.eval()
        correct = 0
        with torch.no_grad():
            for images, labels in val_loader:
                images, labels = images.to(device), labels.to(device)
                outputs = model(images)
                correct += (outputs.argmax(1) == labels).sum().item()
        val_acc = correct / len(val_set)

        print(f"Epoch {epoch + 1}/{args.epochs} - train_loss: {train_loss:.4f} - val_acc: {val_acc:.4f}")

        if val_acc > best_acc:
            best_acc = val_acc
            torch.save(model.state_dict(), output_dir / "best_model.pt")
            (output_dir / "classes.json").write_text(json.dumps(classes, ensure_ascii=False, indent=2))

    print(f"Meilleure précision de validation : {best_acc:.4f}")


def parse_args():
    parser = argparse.ArgumentParser(description="Entraîne un classifieur de type de nuage (CCSN)")
    parser.add_argument("--data-dir", required=True, help="Dossier du dataset (un sous-dossier par classe)")
    parser.add_argument("--output", default="models", help="Dossier de sortie pour le modèle entraîné")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--val-split", type=float, default=0.2)
    parser.add_argument(
        "--no-pretrained",
        dest="pretrained",
        action="store_false",
        help="N'utilise pas les poids ImageNet pré-entraînés (utile sans accès internet)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    train(parse_args())
