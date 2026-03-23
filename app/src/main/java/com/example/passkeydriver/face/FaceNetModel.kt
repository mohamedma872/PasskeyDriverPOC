package com.example.passkeydriver.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FaceNetModel(context: Context) {

    private val TAG = "FaceNetModel"

    private val interpreter: Interpreter? = runCatching {
        val model = loadModel(context)
        val interp = Interpreter(model, Interpreter.Options().apply { numThreads = 2 })
        val inShape  = interp.getInputTensor(0).shape()
        val outShape = interp.getOutputTensor(0).shape()
        Log.i(TAG, "Model loaded — input: ${inShape.toList()}, output: ${outShape.toList()}")
        interp
    }.onFailure {
        Log.e(TAG, "Failed to load facenet.tflite — place it in app/src/main/assets/", it)
    }.getOrNull()

    val isLoaded: Boolean get() = interpreter != null

    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: run {
            Log.w(TAG, "getEmbedding called but model is not loaded")
            return null
        }
        val t0 = System.currentTimeMillis()
        val input  = bitmapToBuffer(faceBitmap)
        val output = Array(1) { FloatArray(512) }
        interp.run(input, output)
        val embedding = l2Normalize(output[0])
        Log.d(TAG, "Embedding computed in ${System.currentTimeMillis() - t0} ms " +
                "(norm=${embedding.map { it * it }.sum().let { Math.sqrt(it.toDouble()) }.let { "%.4f".format(it) }})")
        return embedding
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        val buf = ByteBuffer.allocateDirect(1 * 160 * 160 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(160 * 160)
        scaled.getPixels(pixels, 0, 160, 0, 0, 160, 160)
        for (pixel in pixels) {
            buf.putFloat(((pixel shr 16 and 0xFF) / 128f) - 1f)
            buf.putFloat(((pixel shr 8  and 0xFF) / 128f) - 1f)
            buf.putFloat(((pixel        and 0xFF) / 128f) - 1f)
        }
        return buf
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = Math.sqrt(v.map { it * it }.sum().toDouble()).toFloat()
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd("facenet.tflite")
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
    }

    fun close() = interpreter?.close()
}
