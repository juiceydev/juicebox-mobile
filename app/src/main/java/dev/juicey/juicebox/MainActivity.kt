package dev.juicey.juicebox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.juicey.juicebox.ui.files.FilesScreen
import dev.juicey.juicebox.ui.files.FilesViewModel
import dev.juicey.juicebox.ui.components.LocalJuiceHaptics
import dev.juicey.juicebox.ui.components.rememberJuiceHaptics
import dev.juicey.juicebox.ui.icons.JuiceboxIcons
import dev.juicey.juicebox.ui.icons.juiceboxIcon
import dev.juicey.juicebox.ui.settings.SettingsScreen
import dev.juicey.juicebox.ui.settings.SettingsViewModel
import dev.juicey.juicebox.ui.theme.JuiceboxTheme
import dev.juicey.juicebox.ui.upload.UploadScreen
import dev.juicey.juicebox.ui.upload.UploadViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        val container = (application as JuiceboxApplication).container
        val startTab = if (intent?.getBooleanExtra(EXTRA_OPEN_FILES, false) == true) 1 else 0

        setContent {
            val settings by container.core.settings.collectAsStateWithLifecycle()
            JuiceboxTheme(themeMode = settings.themeMode) {
                val haptics = rememberJuiceHaptics(settings.haptics)
                CompositionLocalProvider(LocalJuiceHaptics provides haptics) {
                    AppRoot(container, startTab)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_FILES = "open_files"
    }
}

@Composable
private fun AppRoot(container: AppContainer, startTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val uploadViewModel: UploadViewModel = viewModel(
        factory = viewModelFactory {
            initializer { UploadViewModel(container) }
        },
    )
    val filesViewModel: FilesViewModel = viewModel(
        factory = viewModelFactory { initializer { FilesViewModel(container) } },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container) } },
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavItem(0, tab, "Upload", JuiceboxIcons.upload) { tab = it }
                NavItem(1, tab, "Files", JuiceboxIcons.files) { tab = it }
                NavItem(2, tab, "Settings", JuiceboxIcons.settings) { tab = it }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                1 -> FilesScreen(filesViewModel, Modifier.fillMaxSize())
                2 -> SettingsScreen(settingsViewModel, Modifier.fillMaxSize())
                else -> UploadScreen(
                    viewModel = uploadViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    index: Int,
    selected: Int,
    label: String,
    icon: Int,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalJuiceHaptics.current
    NavigationBarItem(
        selected = selected == index,
        onClick = {
            haptics.tick()
            onSelect(index)
        },
        icon = {
            Icon(
                juiceboxIcon(icon),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
            )
        },
        label = { Text(label) },
    )
}
