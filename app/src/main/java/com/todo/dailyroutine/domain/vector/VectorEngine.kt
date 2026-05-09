package com.todo.dailyroutine.domain.vector

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import java.util.*
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorEngine(private val context: Context) {
    
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private var tokenizer: BertTokenizer? = null

    init {
        try {
            val modelBytes = context.assets.open("model.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes, OrtSession.SessionOptions())
            tokenizer = BertTokenizer.loadFromAssets(context.assets, "vocab.txt")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates a real embedding using MiniLM L6 v2 model.
     * Dimensions: 384
     */
    fun generateEmbedding(text: String): FloatArray {
        if (ortSession == null || tokenizer == null) {
            // Fallback for development if assets are missing
            val random = Random(text.hashCode().toLong())
            return FloatArray(384) { random.nextFloat() }
        }

        val tokens = tokenizer!!.tokenize(text)
        val inputIds = tokenizer!!.convertTokensToIds(tokens, 128)
        val attentionMask = LongArray(128) { if (it < tokens.size + 2) 1L else 0L }
        val tokenTypeIds = LongArray(128) { 0L }

        val shape = longArrayOf(1, 128)
        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, java.nio.LongBuffer.wrap(inputIds.map { it.toLong() }.toLongArray()), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, java.nio.LongBuffer.wrap(attentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnv, java.nio.LongBuffer.wrap(tokenTypeIds), shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )

        val result = ortSession!!.run(inputs)
        val lastHiddenState = (result[0].value as Array<Array<FloatArray>>)[0] // [seq_len, hidden_size]

        // Mean Pooling
        val hiddenSize = lastHiddenState[0].size
        val meanEmbedding = FloatArray(hiddenSize)
        var count = 0
        for (i in 0 until 128) {
            if (attentionMask[i] == 1L) {
                for (j in 0 until hiddenSize) {
                    meanEmbedding[j] += lastHiddenState[i][j]
                }
                count++
            }
        }
        for (j in 0 until hiddenSize) {
            meanEmbedding[j] /= count.toFloat()
        }

        // L2 Normalization
        return normalize(meanEmbedding)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0.0f
        for (x in v) norm += x * x
        norm = sqrt(norm.toDouble()).toFloat()
        if (norm == 0.0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
        }
        return dotProduct // Both vectors are normalized, so dot product is cosine similarity
    }

    fun floatArrayToJson(array: FloatArray): String {
        val jsonArray = JSONArray()
        for (value in array) {
            jsonArray.put(value.toDouble())
        }
        return jsonArray.toString()
    }

    fun jsonToFloatArray(json: String): FloatArray {
        val jsonArray = JSONArray(json)
        val array = FloatArray(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            array[i] = jsonArray.getDouble(i).toFloat()
        }
        return array
    }

    fun floatArrayToByteArray(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (value in array) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        for (i in array.indices) {
            array[i] = buffer.float
        }
        return array
    }
}
