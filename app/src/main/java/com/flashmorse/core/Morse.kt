package com.flashmorse.core

/** International Morse timing: dot 1, dash 3, intra-element gap 1, letter gap 3, word gap 7. */
object Morse {
    val alphabet = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..",
        'E' to ".", 'F' to "..-.", 'G' to "--.", 'H' to "....",
        'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.",
        'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----",
        '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", '?' to "..--..", '/' to "-..-."
    )
    val reverse = alphabet.entries.associate { (char, pattern) -> pattern to char }
    data class Pulse(val on: Boolean, val units: Int)
    fun encode(text: String): String =
        text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString(" / ") { word ->
                word.mapNotNull { alphabet[it] }.joinToString(" ")
            }
    fun decode(code: String): String =
        code.trim().split(Regex("\\s*/\\s*")).joinToString(" ") { word ->
            word.split(Regex("\\s+")).mapNotNull { reverse[it] }.joinToString("")
        }
    fun pulses(text: String): List<Pulse> {
        val words = text.uppercase().trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }.map { word -> word.mapNotNull { alphabet[it] } }
            .filter { it.isNotEmpty() }
        val out = mutableListOf<Pulse>()
        words.forEachIndexed { wi, codes ->
            codes.forEachIndexed { li, code ->
                code.forEachIndexed { si, c ->
                    out.add(Pulse(true, if (c == '.') 1 else 3))
                    if (si != code.lastIndex) out.add(Pulse(false, 1))
                }
                if (li != codes.lastIndex) out.add(Pulse(false, 3))
            }
            if (wi != words.lastIndex) out.add(Pulse(false, 7))
        }
        return out
    }
    fun durationMs(text: String, wpm: Int): Long =
        pulses(text).sumOf { it.units.toLong() } * (1200L / wpm.coerceIn(1, 25))
}
