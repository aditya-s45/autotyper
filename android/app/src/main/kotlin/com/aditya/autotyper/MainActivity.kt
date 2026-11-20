package com.aditya.autotyper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aditya.autotyper.ui.theme.AutoTyperTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    handleIncomingText(intent)

    enableEdgeToEdge()
    setContent {
      AutoTyperTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIncomingText(intent)
  }

  private fun handleIncomingText(intent: Intent?) {
    val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
    if (!text.isNullOrBlank()) {
      val prefs = getSharedPreferences("autotyper_state", Context.MODE_PRIVATE)
      prefs.edit().putString("saved_code", text).apply()
    }
  }
}
