#!/usr/bin/env bash
# Télécharge le dataset CCSN (Cirrus Cumulus Stratus Nimbus) - 2543 images
# de nuages réparties en 11 classes, structurées en un sous-dossier par classe
# (Ac, Sc, Ns, Cu, Ci, Cc, Cb, As, Ct, Cs, St), directement utilisable avec
# torchvision.datasets.ImageFolder.
#
# Source : https://github.com/upuil/CCSN-Database
set -euo pipefail

DEST_DIR="${1:-data/CCSN}"

if [ -d "$DEST_DIR" ]; then
  echo "Le dossier $DEST_DIR existe déjà, suppression avant re-téléchargement."
  rm -rf "$DEST_DIR"
fi

git clone --depth 1 https://github.com/upuil/CCSN-Database.git "$DEST_DIR"
rm -rf "$DEST_DIR/.git"

echo "Dataset téléchargé dans $DEST_DIR"
