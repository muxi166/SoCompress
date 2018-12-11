package com.hangman.library

interface SpInterface {
    fun saveString(key: String, value: String)
    fun getString(key: String): String
}