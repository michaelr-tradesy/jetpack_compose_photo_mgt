package com.example.photomanagementexercise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import com.example.photomanagementexercise.ui.theme.ColorPalette
import com.example.photomanagementexercise.ui.theme.PhotoManagementExerciseTheme
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

interface AppActivity

abstract class DefaultAppActivity :  ComponentActivity(), AppActivity {

    protected lateinit var activityLauncher: AppActivityResult<Intent, ActivityResult>
    private lateinit var systemUiController: SystemUiController
    private lateinit var appThemeState: MutableState<AppThemeState>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityLauncher =
            AppActivityResult.registerActivityForResult(this)
        ComposeView(this).also {
            setContentView(it)
        }.setContent {
            PostSetContent(savedInstanceState)
        }
    }

    @Composable
    private fun PostSetContent(savedInstanceState: Bundle?) {
        val isDarkMode = isSystemInDarkTheme()
        systemUiController = rememberSystemUiController()
        appThemeState =
            remember {
                mutableStateOf(
                    DefaultAppThemeState(
                        isDarkTheme = isDarkMode,
                        colorPalette = ColorPalette.Orenji
                    )
                )
            }
        PhotoManagementExerciseTheme(
            systemUiController = systemUiController,
            appThemeState = appThemeState.value,
        ) {
            MyApp(savedInstanceState)
        }
    }

    @Composable
    abstract fun MyApp(savedInstanceState: Bundle?)
}