package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingBox() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) = Column(
    Modifier.fillMaxSize().padding(36.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text("!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(18.dp))
    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRetry) { Text("Try again") }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) = Column(
    Modifier.fillMaxSize().padding(36.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) {
        Spacer(Modifier.height(22.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
