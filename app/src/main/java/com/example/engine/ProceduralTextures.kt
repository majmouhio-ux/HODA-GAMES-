package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import com.example.data.model.MapId
import kotlin.random.Random

object ProceduralTextures {
    fun createTextureSet(mapId: MapId): IntArray {
        val textures = IntArray(6)
        GLES30.glGenTextures(6, textures, 0)

        uploadBitmap(textures[0], generateTerrainBitmap(mapId))
        uploadBitmap(textures[1], generateConcreteBitmap(mapId))
        uploadBitmap(textures[2], generateCrateBitmap())
        uploadBitmap(textures[3], generateCamoBitmap(mapId))
        uploadBitmap(textures[4], generateWeaponMetalBitmap())
        uploadBitmap(textures[5], generateArmorVehicleBitmap())

        return textures
    }

    private fun uploadBitmap(texId: Int, bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
    }

    private fun generateTerrainBitmap(mapId: MapId): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val rng = Random(mapId.ordinal * 997 + 42)
        val c1 = mapId.groundPrimaryHex.toInt()
        val c2 = mapId.groundSecondaryHex.toInt()
        val r1 = Color.red(c1); val g1 = Color.green(c1); val b1 = Color.blue(c1)
        val r2 = Color.red(c2); val g2 = Color.green(c2); val b2 = Color.blue(c2)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val wave = (kotlin.math.sin(x * 0.09) * kotlin.math.cos(y * 0.07) * 0.5 + 0.5).toFloat()
                val grain = (rng.nextFloat() - 0.5f) * 28f
                val gridLine = if (mapId == MapId.URBAN_SIEGE && (x % 64 < 2 || y % 64 < 2)) -20f else 0f
                val r = ((r1 * wave + r2 * (1f - wave)) + grain + gridLine).toInt().coerceIn(0, 255)
                val g = ((g1 * wave + g2 * (1f - wave)) + grain + gridLine).toInt().coerceIn(0, 255)
                val b = ((b1 * wave + b2 * (1f - wave)) + grain + gridLine).toInt().coerceIn(0, 255)
                pixels[y * size + x] = Color.rgb(r, g, b)
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp
    }

    private fun generateConcreteBitmap(mapId: MapId): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val baseColor = mapId.structureHex.toInt()
        canvas.drawColor(baseColor)

        val rng = Random(1337)
        val paint = Paint()
        for (i in 0 until 450) {
            val shade = if (rng.nextBoolean()) 255 else 0
            paint.color = Color.argb(rng.nextInt(12, 32), shade, shade, shade)
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            canvas.drawRect(x, y, x + rng.nextFloat() * 14f, y + rng.nextFloat() * 6f, paint)
        }
        paint.color = Color.argb(95, 25, 28, 32)
        paint.strokeWidth = 3f
        for (g in 0 until size step 64) {
            canvas.drawLine(0f, g.toFloat(), size.toFloat(), g.toFloat(), paint)
            canvas.drawLine(g.toFloat(), 0f, g.toFloat(), size.toFloat(), paint)
        }
        // Tactical hazard stripe at base
        paint.color = Color.argb(140, 220, 145, 25)
        canvas.drawRect(0f, size - 14f, size.toFloat(), size - 6f, paint)
        return bmp
    }

    private fun generateCrateBitmap(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(74, 86, 56))
        val p = Paint()
        p.color = Color.rgb(44, 52, 34)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        canvas.drawRect(5f, 5f, size - 5f, size - 5f, p)
        canvas.drawLine(8f, 8f, size - 8f, size - 8f, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(245, 165, 24)
        canvas.drawRect(44f, 52f, 84f, 76f, p)
        return bmp
    }

    private fun generateCamoBitmap(mapId: MapId): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val base = when (mapId) {
            MapId.DESERT_STORM -> Color.rgb(122, 102, 74)
            MapId.URBAN_SIEGE -> Color.rgb(72, 78, 84)
            MapId.ARCTIC_OUTPOST -> Color.rgb(156, 168, 178)
        }
        canvas.drawColor(base)
        val rng = Random(77)
        val p = Paint()
        for (i in 0 until 120) {
            p.color = when (rng.nextInt(3)) {
                0 -> Color.argb(180, 52, 58, 44)
                1 -> Color.argb(170, 96, 82, 58)
                else -> Color.argb(160, 34, 38, 36)
            }
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            canvas.drawRect(x, y, x + rng.nextFloat() * 18f, y + rng.nextFloat() * 12f, p)
        }
        return bmp
    }

    private fun generateWeaponMetalBitmap(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(38, 44, 52))
        val p = Paint()
        for (y in 0 until size step 4) {
            p.color = if ((y / 4) % 2 == 0) Color.argb(25, 255, 255, 255) else Color.argb(30, 0, 0, 0)
            canvas.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), p)
        }
        p.color = Color.rgb(210, 140, 35)
        canvas.drawRect(0f, 56f, size.toFloat(), 62f, p)
        return bmp
    }

    private fun generateArmorVehicleBitmap(): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(66, 72, 62))
        val p = Paint()
        p.color = Color.rgb(42, 46, 40)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        canvas.drawRect(4f, 4f, size - 4f, size - 4f, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(190, 62, 46)
        canvas.drawCircle(64f, 64f, 12f, p)
        return bmp
    }
}
