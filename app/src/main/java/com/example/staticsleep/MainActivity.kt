package com.example.staticsleep

import android.os.Bundle
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val noiseGenerator = StaticNoiseGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toggleButton = findViewById<ToggleButton>(R.id.toggleButton)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startStaticNoise()
            } else {
                stopStaticNoise()
            }
        }
    }

    fun startStaticNoise() {
        val serviceIntent = Intent(this, MusicService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    fun stopStaticNoise() {
        val stopIntent = Intent(this, MusicService::class.java)
        stopService(stopIntent)
    }
}