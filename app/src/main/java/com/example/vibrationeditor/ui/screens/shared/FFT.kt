package com.example.vibrationeditor.ui.screens.shared
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fast Fourier Transform
 * Used to get frequencies magnitudes of a signal
 */
@Serializable
data class FFT(private val n: Int) {
    fun magnitudes(pcm: ShortArray): FloatArray {
        val real = FloatArray(n) { if (it < pcm.size) pcm[it] / 32768f else 0f }
        val imag = FloatArray(n)
        fft(real, imag)
        return FloatArray(n / 2) { i ->
            sqrt((real[i] * real[i] + imag[i] * imag[i]).toDouble()).toFloat()
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * Math.PI / len
            val wRe = cos(ang).toFloat(); val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f; var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]; val uIm = im[i + k]
                    val vRe = re[i+k+len/2]*curRe - im[i+k+len/2]*curIm
                    val vIm = re[i+k+len/2]*curIm + im[i+k+len/2]*curRe
                    re[i+k] = uRe+vRe; im[i+k] = uIm+vIm
                    re[i+k+len/2] = uRe-vRe; im[i+k+len/2] = uIm-vIm
                    val newRe = curRe*wRe - curIm*wIm
                    curIm = curRe*wIm + curIm*wRe; curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
