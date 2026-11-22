package com.tehoubi.ddrtracker.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TextRecognizer's CTC decode stops looking once a character scores past
 * one half, which is only safe if every step's scores are probabilities that
 * sum to 1. This checks the model really ends in a softmax.
 */
class RecognizerOutputTest {
    @Test
    fun `each step's scores are probabilities summing to one`() {
        val env = OrtEnvironment.getEnvironment()
        val dir = File(System.getProperty("ocr.models") ?: "models")
        env.createSession(File(dir, PaddleOcr.REC_MODEL).readBytes()).use { session ->
            val width = 320
            val noise = FloatArray(3 * 48 * width) { ((it * 7919) % 255) / 127.5f - 1f }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(noise), longArrayOf(1, 3, 48, width.toLong())).use { input ->
                session.run(mapOf(session.inputNames.first() to input)).use { out ->
                    val result = out[0] as OnnxTensor
                    val (_, steps, classes) = result.info.shape.map { it.toInt() }
                    val probs = result.floatBuffer
                    for (t in 0 until steps) {
                        var sum = 0.0
                        for (c in 0 until classes) {
                            val p = probs.get(t * classes + c)
                            assertTrue(p >= 0f, "negative score at step $t")
                            sum += p
                        }
                        assertTrue(abs(sum - 1) < 1e-3, "step $t sums to $sum")
                    }
                }
            }
        }
    }
}
