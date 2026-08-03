package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CompletedScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ImportScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoClipperViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to edge immersive design support
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val viewModel: VideoClipperViewModel = viewModel()
                var currentTab by remember { mutableStateOf(0) } // 0 = Import, 1 = Editor, 2 = History
                
                val currentProject by viewModel.selectedProject.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = ContainerGrey,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("main_app_navigation_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                label = { Text("Import", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 0) Icons.Filled.CloudUpload else Icons.Outlined.CloudUpload,
                                        contentDescription = "Import tab"
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryNeon,
                                    selectedTextColor = PrimaryNeon,
                                    indicatorColor = SecondaryNeon,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_import_tab_button")
                            )

                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                label = { Text("Editor", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 1) Icons.Filled.MovieFilter else Icons.Outlined.MovieFilter,
                                        contentDescription = "Editor tab"
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryNeon,
                                    selectedTextColor = PrimaryNeon,
                                    indicatorColor = SecondaryNeon,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                enabled = true, // Editor screen can display an intuitive empty-state which prompts them to import, avoiding dead gates!
                                modifier = Modifier.testTag("nav_editor_tab_button")
                            )

                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                label = { Text("Clips", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 2) Icons.Filled.FolderSpecial else Icons.Outlined.FolderSpecial,
                                        contentDescription = "Clips tab"
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryNeon,
                                    selectedTextColor = PrimaryNeon,
                                    indicatorColor = SecondaryNeon,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_history_tab_button")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            0 -> ImportScreen(
                                viewModel = viewModel,
                                onNavigateToEditor = { currentTab = 1 }
                            )
                            1 -> EditorScreen(
                                viewModel = viewModel,
                                onNavigateToHistory = { currentTab = 2 }
                            )
                            2 -> CompletedScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopBarBranding() {
    Surface(
        color = SurfaceSlate,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Interactive dynamic icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryNeon),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "App Logo",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "LClipz",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

