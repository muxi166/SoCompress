package com.hangman.library

interface DecompressionCallback {
    fun decompression(result: Boolean, hadDecompressed: Boolean)
}