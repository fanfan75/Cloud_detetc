"""Entraîne un classifieur de nuages MobileNetV2 (transfer learning) et l'exporte en TFLite
pour une utilisation embarquée dans l'application Android.
"""
import argparse
import json
from pathlib import Path

import tensorflow as tf
from tensorflow.keras import layers, models

IMG_SIZE = 224


def build_datasets(data_dir: str, val_split: float, batch_size: int, seed: int = 42):
    train_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=val_split,
        subset="training",
        seed=seed,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        data_dir,
        validation_split=val_split,
        subset="validation",
        seed=seed,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
    )
    class_names = train_ds.class_names

    augment = models.Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.05),
        layers.RandomContrast(0.1),
    ])

    train_ds = train_ds.map(lambda x, y: (augment(x, training=True), y))
    autotune = tf.data.AUTOTUNE
    train_ds = train_ds.prefetch(autotune)
    val_ds = val_ds.prefetch(autotune)
    return train_ds, val_ds, class_names


def build_model(num_classes: int) -> tf.keras.Model:
    base = tf.keras.applications.MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet"
    )
    base.trainable = False

    inputs = tf.keras.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(num_classes, activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)
    return model, base


def train(args):
    train_ds, val_ds, class_names = build_datasets(args.data_dir, args.val_split, args.batch_size)
    model, base = build_model(len(class_names))

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs)

    # Fine-tuning : on dégèle les dernières couches du réseau de base pour affiner.
    base.trainable = True
    for layer in base.layers[: -args.fine_tune_layers]:
        layer.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    history = model.fit(train_ds, validation_data=val_ds, epochs=args.fine_tune_epochs)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    (output_dir / "cloud_classifier.tflite").write_bytes(tflite_model)
    (output_dir / "labels.txt").write_text("\n".join(class_names))
    (output_dir / "classes.json").write_text(json.dumps(class_names, ensure_ascii=False, indent=2))

    final_val_acc = history.history["val_accuracy"][-1]
    print(f"Précision finale de validation : {final_val_acc:.4f}")
    print(f"Modèle TFLite exporté dans {output_dir}/cloud_classifier.tflite")


def parse_args():
    parser = argparse.ArgumentParser(description="Entraîne un classifieur de nuages MobileNetV2 -> TFLite")
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--output", default="tflite_model")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--fine-tune-epochs", type=int, default=10)
    parser.add_argument("--fine-tune-layers", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--val-split", type=float, default=0.2)
    return parser.parse_args()


if __name__ == "__main__":
    train(parse_args())
