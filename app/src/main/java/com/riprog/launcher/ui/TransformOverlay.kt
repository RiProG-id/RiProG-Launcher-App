package com.riprog.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riprog.launcher.HomeItem
import com.riprog.launcher.R
import com.riprog.launcher.SettingsManager
import kotlin.math.*

@Composable
fun TransformOverlay(
    item: HomeItem,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onAppInfo: () -> Unit,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var rotation by remember { mutableStateOf(item.rotation) }
    var scaleX by remember { mutableStateOf(item.scaleX) }
    var scaleY by remember { mutableStateOf(item.scaleY) }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.3f))
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onCancel() })
        }
    ) {
        // Action Buttons at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } },
            color = Color(context.getColor(R.color.background)).copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransformButton(text = "REMOVE", onClick = onRemove)
                TransformButton(text = "RESET") {
                    rotation = 0f
                    scaleX = 1f
                    scaleY = 1f
                }
                TransformButton(text = "SAVE") {
                    item.rotation = rotation
                    item.scaleX = scaleX
                    item.scaleY = scaleY
                    onSave()
                }
                TransformButton(text = "APP INFO", onClick = onAppInfo)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .width(200.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Rotation: ${rotation.roundToInt()}°", color = Color.White, fontSize = 12.sp)
                androidx.compose.material.Slider(
                    value = rotation,
                    onValueChange = { rotation = it },
                    valueRange = -180f..180f
                )
                Text("Scale X: ${String.format("%.2f", scaleX)}", color = Color.White, fontSize = 12.sp)
                androidx.compose.material.Slider(
                    value = scaleX,
                    onValueChange = { scaleX = it },
                    valueRange = 0.5f..3.0f
                )
                Text("Scale Y: ${String.format("%.2f", scaleY)}", color = Color.White, fontSize = 12.sp)
                androidx.compose.material.Slider(
                    value = scaleY,
                    onValueChange = { scaleY = it },
                    valueRange = 0.5f..3.0f
                )
            }
        }
    }
}

@Composable
fun TransformButton(text: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Text(
        text = text,
        color = Color(context.getColor(R.color.foreground)),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(8.dp)
            .clickable { onClick() }
    )
}
