package com.riprog.launcher

import android.app.UiModeManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        setContent {
            SettingsScreen(settingsManager, onBack = { finish() })
        }
    }
}

@Composable
fun SettingsScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val bgColor = Color(context.getColor(R.color.background))

    Scaffold(
        backgroundColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CategoryHeader(title = stringResource(R.string.category_home), icon = R.drawable.ic_widgets)
            ToggleSetting(
                title = stringResource(R.string.setting_freeform),
                summary = stringResource(R.string.setting_freeform_summary),
                checked = settingsManager.isFreeformHome,
                onCheckedChange = { settingsManager.isFreeformHome = it }
            )
            ToggleSetting(
                title = stringResource(R.string.setting_hide_labels),
                summary = stringResource(R.string.setting_hide_labels_summary),
                checked = settingsManager.isHideLabels,
                onCheckedChange = { settingsManager.isHideLabels = it }
            )

            CategoryHeader(title = stringResource(R.string.category_appearance), icon = R.drawable.ic_wallpaper)
            ThemeModeSetting(settingsManager)
            ToggleSetting(
                title = stringResource(R.string.setting_liquid_glass),
                summary = stringResource(R.string.setting_liquid_glass_summary),
                checked = settingsManager.isLiquidGlass,
                onCheckedChange = { settingsManager.isLiquidGlass = it }
            )
            ScaleSetting(settingsManager)

            CategoryHeader(title = stringResource(R.string.category_about), icon = R.drawable.ic_info)
            Text(
                text = stringResource(R.string.about_content),
                color = Color(context.getColor(R.color.foreground_dim)),
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun CategoryHeader(title: String, icon: Int) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(context.getColor(R.color.foreground)),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(context.getColor(R.color.foreground))
        )
    }
}

@Composable
fun ToggleSetting(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(checked) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                isChecked = !isChecked
                onCheckedChange(isChecked)
            },
        color = Color(context.getColor(R.color.search_background)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 18.sp, color = Color(context.getColor(R.color.foreground)))
                Text(text = summary, fontSize = 14.sp, color = Color(context.getColor(R.color.foreground_dim)))
            }
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onCheckedChange(it)
                }
            )
        }
    }
}

@Composable
fun ThemeModeSetting(settingsManager: SettingsManager) {
    val context = LocalContext.current
    var currentMode by remember { mutableStateOf(settingsManager.themeMode ?: "system") }
    val modes = listOf("system", "light", "dark")
    val modeLabels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = Color(context.getColor(R.color.search_background)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.setting_theme_mode), fontSize = 18.sp, color = Color(context.getColor(R.color.foreground)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                modes.forEachIndexed { index, mode ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .background(
                                if (isSelected) Color(context.getColor(R.color.foreground)).copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                currentMode = mode
                                settingsManager.themeMode = mode
                            }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(modeLabels[index]),
                            color = if (isSelected) Color(context.getColor(R.color.foreground)) else Color(context.getColor(R.color.foreground_dim)),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScaleSetting(settingsManager: SettingsManager) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(settingsManager.iconScale) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = Color(context.getColor(R.color.search_background)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.setting_scale), fontSize = 18.sp, color = Color(context.getColor(R.color.foreground)))
            Slider(
                value = scale,
                onValueChange = {
                    scale = it
                    settingsManager.iconScale = it
                },
                valueRange = 0.5f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = stringResource(R.string.setting_scale_summary), fontSize = 12.sp, color = Color(context.getColor(R.color.foreground_dim)))
        }
    }
}
