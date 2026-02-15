package com.riprog.launcher
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
        enableEdgeToEdge()
        applyThemeMode(settingsManager.themeMode)
        if (settingsManager.isLiquidGlass()) {
            ThemeUtils.applyWindowBlur(window, true)
        }
        setContent {
            SettingsScreen(
                settingsManager = settingsManager,
                onClose = { finish() },
                onRecreate = { recreate() }
            )
        }
    }
    private fun applyThemeMode(mode: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            var nightMode = UiModeManager.MODE_NIGHT_AUTO
            if ("light" == mode) nightMode = UiModeManager.MODE_NIGHT_NO
            else if ("dark" == mode) nightMode = UiModeManager.MODE_NIGHT_YES
            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }
}
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    onRecreate: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isLiquidGlass = settingsManager.isLiquidGlass()
    val adaptiveColorInt = ThemeUtils.getAdaptiveColor(context, settingsManager, true)
    val adaptiveColor = Color(adaptiveColorInt)
    val secondaryColor = adaptiveColor.copy(alpha = 0.7f)
    val backgroundColorInt = if (isLiquidGlass) {
        context.getColor(R.color.background)
    } else {
        if (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    val backgroundColor = Color(backgroundColorInt)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = null,
                    tint = adaptiveColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.title_settings),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = adaptiveColor
                )
            }
            CategoryHeader(
                title = stringResource(id = R.string.category_home),
                icon = painterResource(id = R.drawable.ic_layout),
                color = adaptiveColor
            )
            var freeform by remember { mutableStateOf(settingsManager.isFreeformHome()) }
            ToggleSetting(
                title = stringResource(id = R.string.setting_freeform),
                summary = stringResource(id = R.string.setting_freeform_summary),
                isChecked = freeform,
                onCheckedChange = {
                    freeform = it
                    settingsManager.setFreeformHome(it)
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            var hideLabels by remember { mutableStateOf(settingsManager.isHideLabels()) }
            ToggleSetting(
                title = stringResource(id = R.string.setting_hide_labels),
                summary = stringResource(id = R.string.setting_hide_labels_summary),
                isChecked = hideLabels,
                onCheckedChange = {
                    hideLabels = it
                    settingsManager.setHideLabels(it)
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            CategoryHeader(
                title = stringResource(id = R.string.category_appearance),
                icon = painterResource(id = R.drawable.ic_wallpaper),
                color = adaptiveColor
            )
            ThemeSetting(
                currentMode = settingsManager.themeMode ?: "system",
                onModeSelected = {
                    settingsManager.themeMode = it
                    onRecreate()
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            ToggleSetting(
                title = stringResource(id = R.string.setting_liquid_glass),
                summary = stringResource(id = R.string.setting_liquid_glass_summary),
                isChecked = isLiquidGlass,
                onCheckedChange = {
                    settingsManager.setLiquidGlass(it)
                    onRecreate()
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            var darkenWallpaper by remember { mutableStateOf(settingsManager.isDarkenWallpaper()) }
            ToggleSetting(
                title = stringResource(id = R.string.setting_darken_wallpaper),
                summary = stringResource(id = R.string.setting_darken_wallpaper_summary),
                isChecked = darkenWallpaper,
                onCheckedChange = {
                    darkenWallpaper = it
                    settingsManager.setDarkenWallpaper(it)
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            var iconScale by remember { mutableFloatStateOf(settingsManager.iconScale) }
            ScaleSetting(
                scale = iconScale,
                onScaleChange = {
                    iconScale = it
                    settingsManager.iconScale = it
                },
                textColor = adaptiveColor,
                secondaryColor = secondaryColor
            )
            CategoryHeader(
                title = stringResource(id = R.string.category_about),
                icon = painterResource(id = R.drawable.ic_info),
                color = adaptiveColor
            )
            Text(
                text = stringResource(id = R.string.about_content),
                color = secondaryColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = adaptiveColor.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
@Composable
fun CategoryHeader(title: String, icon: Painter, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, top = 32.dp, bottom = 16.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
@Composable
fun ToggleSetting(
    title: String,
    summary: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: Color,
    secondaryColor: Color
) {
    Surface(
        onClick = { onCheckedChange(!isChecked) },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 18.sp, color = textColor)
                Text(text = summary, fontSize = 14.sp, color = secondaryColor)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}
@Composable
fun ThemeSetting(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    textColor: Color,
    secondaryColor: Color
) {
    val context = LocalContext.current
    val modes = arrayOf(
        stringResource(id = R.string.theme_system),
        stringResource(id = R.string.theme_light),
        stringResource(id = R.string.theme_dark)
    )
    val values = arrayOf("system", "light", "dark")
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(id = R.string.setting_theme_mode), fontSize = 18.sp, color = textColor)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                values.forEachIndexed { index, value ->
                    val isSelected = value == currentMode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .background(
                                color = if (isSelected) Color(context.getColor(R.color.search_background)) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onModeSelected(value) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = modes[index],
                            fontSize = 14.sp,
                            color = if (isSelected) textColor else secondaryColor
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun ScaleSetting(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    textColor: Color,
    secondaryColor: Color
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(id = R.string.setting_scale), fontSize = 18.sp, color = textColor)
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.5f..1.5f
            )
            Text(text = stringResource(id = R.string.setting_scale_summary), fontSize = 12.sp, color = secondaryColor)
        }
    }
}
