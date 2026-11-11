package com.jp.otherworld.ui.wheel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.json.JSONObject

/**
 * The user's wheel art. `floor` is the original with the painted slabs removed
 * (art/derived/floor_clean.png); `slabs` are the six front slabs cut from the original
 * (art/derived/slab_*.png); `ring` is the 12 slab outlines measured from the original,
 * keyed by angle around the ring (0° = facing you). Quads are TL, TR, BR, BL in image pixels.
 */
class WheelArt(
    val floor: Bitmap,
    val width: Int,
    val height: Int,
    val slabs: Map<String, Slab>,
    val ring: List<Key>
) {
    class Slab(val sprite: Bitmap, val left: Float, val top: Float, val quad: FloatArray)
    class Key(val theta: Float, val quad: FloatArray, val slab: Slab, val mirror: Boolean)
}

/** How to recolour the wheel's glass: hue turned by `hue` degrees from the art's own cyan, saturation scaled by `sat`. */
class WheelTint(val hue: Float, val sat: Float = 1f) {
    fun filter(): ColorMatrixColorFilter {
        val rad = Math.toRadians(hue.toDouble())
        val c = Math.cos(rad).toFloat(); val s = Math.sin(rad).toFloat()
        // Hue rotation that keeps brightness (standard luminance weights), then the saturation scale.
        val m = ColorMatrix(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        if (sat != 1f) m.postConcat(ColorMatrix().apply { setSaturation(sat) })
        return ColorMatrixColorFilter(m)
    }
}

/** The wheel's colour for the live wallpaper in use (its service class name); null = the art as drawn. */
fun wheelTintFor(service: String?): WheelTint? = when (service?.substringAfterLast('.')) {
    "AlienGreenWallpaperService" -> WheelTint(-75f)
    "AlienPurpleWallpaperService" -> WheelTint(80f)
    "AlienPinkWallpaperService" -> WheelTint(125f)
    "AlienRedWallpaperService" -> WheelTint(160f)
    "AlienGoldWallpaperService" -> WheelTint(-150f)
    "AlienIceWallpaperService" -> WheelTint(0f, 0.25f)
    "AlienBlueWallpaperService" -> WheelTint(30f)
    else -> null
}

private fun Bitmap.tinted(tint: WheelTint?): Bitmap {
    if (tint == null) return this
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(this, 0f, 0f, Paint().apply { colorFilter = tint.filter() })
    return out
}

fun loadWheelArt(context: Context, tint: WheelTint? = null): WheelArt {
    val json = JSONObject(context.assets.open("wheel.json").bufferedReader().use { it.readText() })
    fun bitmap(name: String) = context.assets.open(name).use { BitmapFactory.decodeStream(it) }.tinted(tint)
    fun quad(o: JSONObject) = o.getJSONArray("quad").let { q -> FloatArray(8) { q.getDouble(it).toFloat() } }
    val slabs = json.getJSONArray("slots").let { arr ->
        (0 until arr.length()).associate { i ->
            val s = arr.getJSONObject(i)
            s.getString("id") to WheelArt.Slab(
                sprite = bitmap(s.getString("sprite")),
                left = s.getDouble("left").toFloat(),
                top = s.getDouble("top").toFloat(),
                quad = quad(s)
            )
        }
    }
    val ring = json.getJSONArray("ring").let { arr ->
        (0 until arr.length()).map { i ->
            val k = arr.getJSONObject(i)
            WheelArt.Key(k.getDouble("theta").toFloat(), quad(k), slabs.getValue(k.getString("slab")), k.getBoolean("mirror"))
        }.sortedBy { it.theta }
    }
    return WheelArt(bitmap(json.getString("floor")), json.getInt("width"), json.getInt("height"), slabs, ring)
}
