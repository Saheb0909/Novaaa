package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.NovaScreen
import com.example.ui.NovaViewModel
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NovaAssistantTheme

class MainActivity : ComponentActivity() {

  private val viewModel: NovaViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      NovaAssistantTheme(darkTheme = true) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = DarkBackground
        ) {
          NovaScreen(viewModel = viewModel)
        }
      }
    }
  }
}

