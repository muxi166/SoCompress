package com.hangman.library

import android.content.Context

class InjectLibSoPath {

    companion object Instance {
        var applicationContext: Context? = null

        @JvmStatic
        fun inject(context: Context) {
            applicationContext = context
        }
    }

}