package com.riprog.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riprog.launcher.AppItem
import com.riprog.launcher.HomeItem
import com.riprog.launcher.LauncherModel
import com.riprog.launcher.R
import com.riprog.launcher.SettingsManager

@Composable
fun FolderOverlay(
    folder: HomeItem,
    model: LauncherModel,
    settingsManager: SettingsManager,
    onAppClick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var folderName by remember { mutableStateOf(folder.folderName ?: "Folder") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clickable(enabled = false) { }, // Consume clicks
            color = Color(context.getColor(R.color.background)).copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            elevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        folder.folderName = it
                        // Persist is handled via saveHomeState in Activity
                    },
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(context.getColor(R.color.foreground))
                    ),
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val items = folder.folderItems ?: emptyList()
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(items) { subItem ->
                            AppItemView(
                                app = AppItem("", subItem.packageName ?: "", subItem.className ?: ""),
                                model = model,
                                iconScale = settingsManager.iconScale,
                                onClick = {
                                    onAppClick(subItem.packageName ?: "")
                                    onClose()
                                },
                                onLongClick = { /* Handle removal from folder */ }
                            )
                        }
                    }
                }
            }
        }
    }
}
