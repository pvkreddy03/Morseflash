package com.flashmorse.core

/** Monotonic timestamp input; rudimentary decoder for experimental camera receiver. */
class LightDecoder(var dotMs: Long = 240L) {
    var text = ""
        private set
    var symbols = ""
        private set
    private var initialized = false
    private var state = false
    private var edge = 0L
    fun reset() { text = ""; symbols = ""; initialized = false; state = false; edge = 0L }
    fun accept(on: Boolean, timeMs: Long) {
        if (!initialized) { initialized = true; state = on; edge = timeMs; return }
        if (timeMs < edge) return
        val elapsed = timeMs - edge
        if (state == on) {
            if (!on && elapsed >= dotMs * 2) finishLetter()
            if (!on && elapsed >= dotMs * 6 && text.isNotBlank() && !text.endsWith(" ")) text += " "
            return
        }
        if (elapsed < maxOf(35L, dotMs / 3)) return
        if (state) {
            symbols += if (elapsed < dotMs * 2) "." else "-"
        } else {
            if (elapsed >= dotMs * 2) finishLetter()
            if (elapsed >= dotMs * 6 && text.isNotBlank() && !text.endsWith(" ")) text += " "
        }
        edge = timeMs
        state = on
    }
    private fun finishLetter() {
        if (symbols.isNotEmpty()) {
            text += Morse.reverse[symbols] ?: '?'
            symbols = ""
        }
    }
    fun finish() { finishLetter(); text = text.trimEnd() }
}
