package com.example.photomanagementexercise

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultCaller.*

class AppRequestPermissionsResult {
    private val activityResultCallback : ActivityResultCallback<Boolean> = object : ActivityResultCallback<Boolean> {
        override fun onActivityResult(result: Boolean?) {
            if(result == true) {
                println("Permission Granted")
            } else {
                println("Permission Denied")
            }
        }
    }
    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            println("${it.key} = ${it.value}")
        }
    }
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission(), activityResultCallback)
}
