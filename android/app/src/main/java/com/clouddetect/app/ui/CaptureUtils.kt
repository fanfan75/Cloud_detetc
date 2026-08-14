package com.clouddetect.app.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import java.io.OutputStream

private const val TAG = "CaptureUtils"
private const val MARGIN = 36
private const val TITLE_SIZE = 52f
private const val SUBTITLE_SIZE = 34f
private const val BODY_SIZE = 30f
private const val OUTPUT_WIDTH = 1080

/**
 * Compose une image partageable : la photo du ciel en haut, la fiche du nuage en dessous.
 */
fun buildCaptureBitmap(
    photo: Bitmap,
    title: String,
    subtitle: String,
    lines: List<String>,
): Bitmap {
    val titlePaint = TextPaint().apply {
        color = Color.BLACK
        textSize = TITLE_SIZE
        isAntiAlias = true
        isFakeBoldText = true
    }
    val subtitlePaint = TextPaint().apply {
        color = Color.rgb(60, 90, 150)
        textSize = SUBTITLE_SIZE
        isAntiAlias = true
    }
    val bodyPaint = TextPaint().apply {
        color = Color.rgb(40, 40, 40)
        textSize = BODY_SIZE
        isAntiAlias = true
    }

    val textWidth = OUTPUT_WIDTH - 2 * MARGIN
    val titleLayout = buildLayout(title, titlePaint, textWidth)
    val subtitleLayout = buildLayout(subtitle, subtitlePaint, textWidth)
    val bodyLayouts = lines.map { buildLayout(it, bodyPaint, textWidth) }

    val photoHeight = (photo.height.toFloat() * OUTPUT_WIDTH / photo.width).toInt().coerceAtLeast(1)

    var textHeight = MARGIN + titleLayout.height + MARGIN / 3 + subtitleLayout.height + MARGIN / 2
    bodyLayouts.forEach { textHeight += it.height + MARGIN / 2 }
    textHeight += MARGIN

    val result = Bitmap.createBitmap(OUTPUT_WIDTH, photoHeight + textHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.WHITE)

    val scaledPhoto = Bitmap.createScaledBitmap(photo, OUTPUT_WIDTH, photoHeight, true)
    canvas.drawBitmap(scaledPhoto, 0f, 0f, null)

    var y = photoHeight + MARGIN.toFloat()
    y = drawLayout(canvas, titleLayout, y) + MARGIN / 3f
    y = drawLayout(canvas, subtitleLayout, y) + MARGIN / 2f
    bodyLayouts.forEach { layout ->
        y = drawLayout(canvas, layout, y) + MARGIN / 2f
    }

    return result
}

private fun drawLayout(canvas: Canvas, layout: StaticLayout, y: Float): Float {
    canvas.save()
    canvas.translate(MARGIN.toFloat(), y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height
}

private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(4f, 1f)
        .setIncludePad(false)
        .build()

/**
 * Enregistre l'image dans la galerie (Pictures/CloudDetect) et retourne true en cas de succès.
 */
fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CloudDetect")
        }
    }

    return try {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e(TAG, "MediaStore n'a pas fourni d'URI pour l'enregistrement")
            return false
        }
        resolver.openOutputStream(uri)?.use { stream: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        } ?: false
    } catch (e: Exception) {
        Log.e(TAG, "Échec de l'enregistrement de la capture", e)
        false
    }
}
