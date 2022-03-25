package com.example.photomanagementexercise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import com.example.photomanagementexercise.access.CheckAccessByVersionWrapper
import com.example.photomanagementexercise.access.CheckPermissionUtility
import com.example.photomanagementexercise.access.DefaultCheckAccessByVersionWrapper
import com.example.photomanagementexercise.access.DefaultCheckPermissionUtility
import com.example.photomanagementexercise.ui.theme.ColorPalette
import com.example.photomanagementexercise.ui.theme.PhotoManagementExerciseTheme
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController


interface AppActivity

abstract class DefaultAppActivity : ComponentActivity(), AppActivity {

    protected lateinit var activityLauncher: AppActivityResult<Intent, ActivityResult>
    protected lateinit var checkPermissionUtility: CheckPermissionUtility
    private lateinit var checkAccessByVersionWrapper: CheckAccessByVersionWrapper
    private lateinit var systemUiController: SystemUiController
    private lateinit var appThemeState: MutableState<AppThemeState>
    protected lateinit var requestPermissionLauncher: AppActivityResult<String, ActivityResult>
    val requestPermission = ActivityResultContracts.RequestPermission()

    //    val requestPermissionLauncher: ActivityResultLauncher<String> =
//        registerForActivityResult(requestPermission) { isGranted ->
//            if (isGranted) {
//                // Permission is granted. Continue the action or workflow in your app.
//                checkPermissionUtility.continueRequestingPermissions()
//            } else {
//                // Explain to the user that the feature is unavailable because the
//                // features requires a permission that the user has denied. At the
//                // same time, respect the user's decision. Don't link to system
//                // settings in an effort to convince the user to change their
//                // decision.
//                checkPermissionUtility.onPermissionDenied()
//            }
//        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityLauncher =
            AppActivityResult.registerActivityForResult(this)
//        requestPermissionLauncher =
//            AppRequestPermissionsResult.registerActivityForResult(this)
        checkAccessByVersionWrapper = DefaultCheckAccessByVersionWrapper(
            activityLauncher,
//            requestPermissionLauncher
        )
        checkPermissionUtility = DefaultCheckPermissionUtility(checkAccessByVersionWrapper)
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