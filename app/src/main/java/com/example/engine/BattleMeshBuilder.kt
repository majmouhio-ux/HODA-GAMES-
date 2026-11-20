package com.example.engine

import android.opengl.GLES30
import com.example.data.model.MapId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GpuMesh(val vboId: Int, val vertexCount: Int) {
    fun bindAndDraw() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    }
}

object BattleMeshBuilder {
    const val ARENA_HALF_SIZE = 85f

    fun getTerrainHeight(mapId: MapId, x: Float, z: Float): Float {
        val distCenter = sqrt(x * x + z * z)
        val rimLift = ((distCenter - 58f).coerceAtLeast(0f) * 0.22f)
        return when (mapId) {
            MapId.DESERT_STORM -> {
                (sin(x * 0.08f) * cos(z * 0.07f) * 1.6f +
                    sin(x * 0.03f + z * 0.04f) * 1.1f + rimLift)
            }
            MapId.URBAN_SIEGE -> {
                (sin(x * 0.05f) * cos(z * 0.05f) * 0.45f + rimLift * 0.6f)
            }
            MapId.ARCTIC_OUTPOST -> {
                (sin(x * 0.09f) * cos(z * 0.08f) * 2.1f +
                    cos(x * 0.04f - z * 0.03f) * 1.2f + rimLift)
            }
        }
    }

    fun getTerrainNormal(mapId: MapId, x: Float, z: Float): FloatArray {
        val eps = 0.6f
        val hL = getTerrainHeight(mapId, x - eps, z)
        val hR = getTerrainHeight(mapId, x + eps, z)
        val hD = getTerrainHeight(mapId, x, z - eps)
        val hU = getTerrainHeight(mapId, x, z + eps)
        val nx = hL - hR
        val ny = 2f * eps
        val nz = hD - hU
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        return floatArrayOf(nx / len, ny / len, nz / len)
    }

    fun buildTerrainMesh(mapId: MapId, step: Int): GpuMesh {
        val gridCount = 64 / step.coerceIn(1, 2)
        val cellSize = (ARENA_HALF_SIZE * 2f) / gridCount
        val verts = ArrayList<Float>(gridCount * gridCount * 6 * 8)

        fun pushVertex(x: Float, z: Float) {
            val y = getTerrainHeight(mapId, x, z)
            val n = getTerrainNormal(mapId, x, z)
            verts.add(x); verts.add(y); verts.add(z)
            verts.add(n[0]); verts.add(n[1]); verts.add(n[2])
            verts.add(x * 0.14f); verts.add(z * 0.14f)
        }

        for (gz in 0 until gridCount) {
            for (gx in 0 until gridCount) {
                val x0 = -ARENA_HALF_SIZE + gx * cellSize
                val x1 = x0 + cellSize
                val z0 = -ARENA_HALF_SIZE + gz * cellSize
                val z1 = z0 + cellSize

                pushVertex(x0, z0); pushVertex(x0, z1); pushVertex(x1, z1)
                pushVertex(x0, z0); pushVertex(x1, z1); pushVertex(x1, z0)
            }
        }
        return uploadMesh(verts)
    }

    fun buildUnitCubeMesh(): GpuMesh {
        val verts = ArrayList<Float>(36 * 8)
        fun face(
            p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray,
            nx: Float, ny: Float, nz: Float
        ) {
            fun add(p: FloatArray, u: Float, v: Float) {
                verts.add(p[0]); verts.add(p[1]); verts.add(p[2])
                verts.add(nx); verts.add(ny); verts.add(nz)
                verts.add(u); verts.add(v)
            }
            add(p0, 0f, 1f); add(p1, 0f, 0f); add(p2, 1f, 0f)
            add(p0, 0f, 1f); add(p2, 1f, 0f); add(p3, 1f, 1f)
        }
        val h = 0.5f
        face(floatArrayOf(-h, -h, h), floatArrayOf(-h, h, h), floatArrayOf(h, h, h), floatArrayOf(h, -h, h), 0f, 0f, 1f)
        face(floatArrayOf(h, -h, -h), floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h), floatArrayOf(-h, -h, -h), 0f, 0f, -1f)
        face(floatArrayOf(-h, -h, -h), floatArrayOf(-h, h, -h), floatArrayOf(-h, h, h), floatArrayOf(-h, -h, h), -1f, 0f, 0f)
        face(floatArrayOf(h, -h, h), floatArrayOf(h, h, h), floatArrayOf(h, h, -h), floatArrayOf(h, -h, -h), 1f, 0f, 0f)
        face(floatArrayOf(-h, h, h), floatArrayOf(-h, h, -h), floatArrayOf(h, h, -h), floatArrayOf(h, h, h), 0f, 1f, 0f)
        face(floatArrayOf(-h, -h, -h), floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h), floatArrayOf(h, -h, -h), 0f, -1f, 0f)
        return uploadMesh(verts)
    }

    fun buildRingWallMesh(segments: Int = 48): GpuMesh {
        val verts = ArrayList<Float>(segments * 6 * 8)
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
            val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
            val x0 = cos(a0); val z0 = sin(a0)
            val x1 = cos(a1); val z1 = sin(a1)
            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments

            fun push(x: Float, y: Float, z: Float, u: Float, v: Float) {
                verts.add(x); verts.add(y); verts.add(z)
                verts.add(-x); verts.add(0f); verts.add(-z)
                verts.add(u); verts.add(v)
            }
            push(x0, 0f, z0, u0, 0f)
            push(x0, 1f, z0, u0, 1f)
            push(x1, 1f, z1, u1, 1f)
            push(x0, 0f, z0, u0, 0f)
            push(x1, 1f, z1, u1, 1f)
            push(x1, 0f, z1, u1, 0f)
        }
        return uploadMesh(verts)
    }

    private fun uploadMesh(data: List<Float>): GpuMesh {
        val array = data.toFloatArray()
        val buf = ByteBuffer.allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(array)
        buf.position(0)

        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, array.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return GpuMesh(vbo[0], array.size / 8)
    }
}
