package com.clouddetect.app.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

data class ClassificationResult(val code: String, val confidence: Float)

/**
 * Charge le modèle cloud_classifier.tflite (MobileNetV2, normalisation intégrée au graphe)
 * et classifie une image de ciel parmi les 11 genres de nuages CCSN.
 */
class CloudClassifier(context: Context) {
    private val interpreter: Interpreter
    val labels: List<String>
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_FILE)
        interpreter = Interpreter(model)
        labels = FileUtil.loadLabels(context, LABELS_FILE)
    }

    /** Retourne les `topK` classes les plus probables, de la plus probable à la moins probable. */
    fun classify(bitmap: Bitmap, topK: Int = 3): List<ClassificationResult> {
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(tensorImage.buffer, output)

        val probs = output[0]
        return probs.indices
            .sortedByDescending { probs[it] }
            .take(topK)
            .map { ClassificationResult(labels[it], probs[it]) }
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "cloud_classifier.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val INPUT_SIZE = 224
    }
}
