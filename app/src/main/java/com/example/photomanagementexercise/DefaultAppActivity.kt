package com.example.photomanagementexercise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
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

    private lateinit var systemUiController: SystemUiController
    private lateinit var appThemeState: MutableState<AppThemeState>
//    private val activityResultCallback: ActivityResultCallback<ActivityResult> =
//        ActivityResultCallback<ActivityResult> { result ->
//            println("MROEBUCK: result=[$result]")
//            if (result.resultCode == RESULT_OK) {
//                // There are no request codes
//                val data = result.data
//                val bitmap = data?.data
//                println("MROEBUCK: YAY!!")
//            }
//        }
//    protected val activityResultLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult(),
//            activityResultCallback
//        )
//    private val requestPermissionCallback: ActivityResultCallback<Boolean> =
//        ActivityResultCallback<Boolean> { result ->
//            if (result == true) {
//                println("Permission Granted")
//            } else {
//                println("Permission Denied")
//            }
//        }
//    protected val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission(),
//            requestPermissionCallback
//        )
    protected val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                println("${it.key} = ${it.value}")
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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