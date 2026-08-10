# Cloud Detect

Détection et reconnaissance du type de nuage à partir d'une image, avec mise en
relation du type détecté et du temps qu'il annonce généralement. Optionnellement,
la météo actuelle du lieu peut être récupérée via une API pour la comparer à
l'association typique du nuage détecté.

## Dataset

Ce projet utilise par défaut le dataset **CCSN** (Cirrus Cumulus Stratus Nimbus) :
2543 images de nuages en 256×256, réparties en 11 classes selon la classification
de l'OMM (Organisation Météorologique Mondiale) : `Ac, Sc, Ns, Cu, Ci, Cc, Cb, As,
Ct, Cs, St`.

- Source : https://github.com/upuil/CCSN-Database
- Miroir Kaggle : https://www.kaggle.com/datasets/mmichelli/cirrus-cumulus-stratus-nimbus-ccsn-database

Autres datasets utilisables (structure similaire, un sous-dossier par classe) :

- **SWIMCAT** (784 images, 5 classes de ciel/nuages) : https://malea.winkler.site/swimcat.html
- **MGCD** (8000 images avec métadonnées météo intégrées — température, humidité,
  pression, vent — mais nécessite de signer un accord d'usage auprès des auteurs) :
  https://github.com/shuangliutjnu/Multimodal-Ground-based-Cloud-Database

Vérifiez les conditions d'usage/licence de chaque dataset avant tout usage commercial.

### Télécharger le dataset CCSN

```bash
bash scripts/download_ccsn.sh
```

Le dataset est cloné dans `data/CCSN/`, avec un sous-dossier par classe
(compatible `torchvision.datasets.ImageFolder`).

## Installation

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Entraînement

```bash
python src/train.py --data-dir data/CCSN --output models --epochs 15
```

Le meilleur modèle (`best_model.pt`) et la liste des classes (`classes.json`)
sont sauvegardés dans le dossier `models/`.

## Prédiction

```bash
python src/predict.py --image chemin/vers/photo.jpg --checkpoint models
```

Affiche le type de nuage détecté, la confiance du modèle, et l'association
météo typique (ex : "Cumulonimbus → orage, fortes pluies, grêle possible").

Pour comparer avec la météo réelle actuelle du lieu (via l'API gratuite
[Open-Meteo](https://open-meteo.com), sans clé requise) :

```bash
python src/predict.py --image chemin/vers/photo.jpg --checkpoint models --lat 48.85 --lon 2.35
```

## Structure du projet

```
scripts/
  download_ccsn.sh   # script de téléchargement du dataset CCSN
src/
  dataset.py         # chargement du dataset + table type de nuage → météo typique
  train.py           # entraînement (ResNet18 pré-entraîné, fine-tuning)
  predict.py         # inférence sur une image + météo actuelle optionnelle
  weather.py         # appel à l'API Open-Meteo
```
