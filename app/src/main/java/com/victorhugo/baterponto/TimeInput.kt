package com.victorhugo.baterponto

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Keep only four clock digits; reject invalid edits instead of silently changing hours. */
fun acceptTimeInput(previous: String, proposed: String): String {
    if (proposed.any { it !in '0'..'9' && it != ':' }) return previous
    val digits = proposed.replace(":", "")
    if (digits.length > 4) return previous
    if (digits.isNotEmpty() && digits[0] > '2') return previous
    if (digits.length >= 2 && digits.take(2).toInt() > 23) return previous
    if (digits.length >= 3 && digits[2] > '5') return previous
    return digits
}

fun timeInputMinutes(digits: String): Int? {
    if (digits.length != 4 || acceptTimeInput("", digits) != digits) return null
    return digits.take(2).toInt() * 60 + digits.takeLast(2).toInt()
}

object TimeMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val hasColon = digits.length >= 2
        val formatted = if (hasColon) digits.take(2) + ":" + digits.drop(2) else digits
        return TransformedText(AnnotatedString(formatted), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (hasColon && offset >= 2) offset + 1 else offset
            override fun transformedToOriginal(offset: Int): Int =
                (if (hasColon && offset > 2) offset - 1 else offset).coerceAtMost(digits.length)
        })
    }
}
