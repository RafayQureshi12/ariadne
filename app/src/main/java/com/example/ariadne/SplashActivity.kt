// src/main/java/com/yourpackage/SplashActivity.kt
package com.example.ariadne

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val splashTimeOut: Long = 4550 // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({



            // Start main activity
            startActivity(Intent(this, MainActivity::class.java))
            // Close splash activity
            finish()
        }, splashTimeOut)
    }
}
