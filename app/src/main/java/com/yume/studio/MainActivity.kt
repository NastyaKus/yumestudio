package com.yume.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yume.studio.data.Settings
import com.yume.studio.ui.home.HomeScreen
import com.yume.studio.ui.studio.StudioScreen
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.YumeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as YumeApp
        setContent {
            val settings by app.prefs.settings.collectAsState(initial = Settings())
            YumeTheme(settings.theme) { YumeNav() }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as YumeApp).engine.resume()
    }

    override fun onStop() {
        super.onStop()
        // в фоне поток не держим — экономим батарею
        if (!isChangingConfigurations) (application as YumeApp).engine.pause()
    }
}

@Composable
private fun YumeNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenProject = { id -> nav.navigate("studio/$id") })
        }
        composable("studio/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val app = LocalContext.current.applicationContext as YumeApp
            val vm: StudioViewModel = viewModel(key = "studio-$id", factory = viewModelFactory {
                initializer { StudioViewModel(app, id) }
            })
            StudioScreen(vm, onExit = { nav.popBackStack() })
        }
    }
}
