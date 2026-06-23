package com.enaboapps.switchify.pc

const val PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH = 2_000
const val PC_TEXT_STREAM_CHUNK_CODE_POINTS = 4

sealed class PcTextStreamItem {
    data class Chunk(val text: String) : PcTextStreamItem()
    data class Key(val key: PcKeyboardKey) : PcTextStreamItem()
}

fun isSafePcTypedText(text: String): Boolean {
    if (text.length > PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH) return false
    return text.none { char ->
        val code = char.code
        (code in 0x00..0x1f && char != '\t' && char != '\n' && char != '\r') ||
                code in 0x7f..0x9f
    }
}

fun pcTextStreamItemsFor(text: String): List<PcTextStreamItem> {
    val items = mutableListOf<PcTextStreamItem>()
    val chunk = StringBuilder()
    var chunkCodePoints = 0

    fun flushChunk() {
        if (chunk.isNotEmpty()) {
            items += PcTextStreamItem.Chunk(chunk.toString())
            chunk.clear()
            chunkCodePoints = 0
        }
    }

    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val itemText = String(Character.toChars(codePoint))
        when (itemText) {
            "\n", "\r" -> {
                flushChunk()
                items += PcTextStreamItem.Key(PcKeyboardKey.Enter)
            }
            "\t" -> {
                flushChunk()
                items += PcTextStreamItem.Key(PcKeyboardKey.Tab)
            }
            else -> {
                chunk.append(itemText)
                chunkCodePoints += 1
                if (chunkCodePoints >= PC_TEXT_STREAM_CHUNK_CODE_POINTS) {
                    flushChunk()
                }
            }
        }
        index += Character.charCount(codePoint)
    }
    flushChunk()
    return items
}
