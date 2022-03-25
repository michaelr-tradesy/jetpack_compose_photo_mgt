package com.example.photomanagementexercise.access

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.photomanagementexercise.AppActivityResult
import com.example.photomanagementexercise.AppActivityResult.Companion.registerForActivityResult


/**
 * @author Coach Roebuck
 * This interface serves as a wrapper to access various static methods
 * @since 2.17
 */
interface CheckAccessByVersionWrapper {
    /**
     * @author Coach Roebuck
     * This method is self explanatory: whether or not we show the request permission rationale.
     * @param activity The specified activity
     * @param permission The permission in question, preferably of AccessPermissionType.name
     * @return True if condition is met; otherwise, false
     * @since 2.17
     */
    fun shouldShowRequestPermissionRationale(
        activity: Activity,
        permission: String): Boolean

    /**
     * @author Coach Roebuck
     * This method is self explanatory: request permissions
     * @param activity The specified activity
     * @param permission The permission, preferably of AccessPermissionType.name
     * @param identifier The identifier of the permission,
     *  preferably of AccessPermissionType.identifier
     * @since 2.17
     */
    fun requestPermissions(
        activity: Activity,
        permission: String,
        identifier: Int)

    /**
     * @author Coach Roebuck
     * This applies to Android 29 and above.
     * This method requests permission to manage external storage on the user's device.
     * @param activity The specified activity
     * @param identifier The identifier of the permission,
     *  preferably of AccessPermissionType.identifier
     * @since 2.17
     */
    fun requestManageExternalStoragePermissions(
        activity: Activity,
        identifier: Int
    )
    /**
     * @author Coach Roebuck
     * This method is self explanatory: request permissions
     * @param activity The specified activity
     * @param permission The permission, preferably of AccessPermissionType.name
     * @return  PackageManager.PERMISSION_GRANTED if permission is granted;
     *  Otherwise, PackageManager.PERMISSION_DENIED
     * @since 2.17
     */
    fun checkSelfPermission(
        activity: Activity,
        permission: String
    ): Int

    /**
     * @author Coach Roebuck
     * This method is self explanatory: whether or not we can access the external storage manager.
     * @param activity The specified activity
     * @param currentPermission The permission of AccessPermissionType
     * @return True if condition is met; otherwise, false
     * @since 2.17
     */
    fun canRequestManageExternalStoragePermissions(
        activity: Activity,
        currentPermission: AccessPermissionType
    ): Boolean

    /**
     * @author Coach Roebuck
     * This method is self explanatory: whether or not we can access the write external storage.
     * @param currentPermission The permission of AccessPermissionType
     * @return True if condition is met; otherwise, false
     * @since 2.17
     */
    fun canAccessWriteExternalStorage(currentPermission: AccessPermissionType): Boolean
    fun canCheckNormalPermission(): Boolean
}

/**
 * @author Coach Roebuck
 * This interface serves the default implementation of the CheckAccessByVersionWrapper interface.
 * @since 2.17
 */
class DefaultCheckAccessByVersionWrapper(
    private val activityLauncher: AppActivityResult<Intent, ActivityResult>,
    private val requestPermissionLauncher: AppActivityResult<String, ActivityResult>
    ) : CheckAccessByVersionWrapper {

    override fun shouldShowRequestPermissionRationale(
        activity: Activity,
        permission: String
    ): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            permission
        )
    }

    override fun requestManageExternalStoragePermissions(
        activity: Activity,
        identifier: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(
                    java.lang.String.format(
                        "package:%s",
                        activity.applicationContext.packageName
                    )
                )
                intent
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                intent
            }
            activityLauncher.launch(intent, object :
                AppActivityResult.OnActivityResult<ActivityResult> {
                override fun onActivityResult(result: ActivityResult) {
                    if (result.resultCode == ComponentActivity.RESULT_OK) {
                        // There are no request codes
                        val data = result.data
                        val bitmap = data?.data
                        println("MROEBUCK: YAY!!")
                    }
                }
            })
        } else {
            requestPermissions(
                activity,
                AccessPermissionType.AccessWriteExternalStorage.permission,
                identifier
            )
        }
    }
    override fun requestPermissions(activity: Activity, permission: String, identifier: Int) {
        requestPermissionLauncher.launch(permission, object :
            AppActivityResult.OnActivityResult<ActivityResult> {
            override fun onActivityResult(result: ActivityResult) {
                if (result.resultCode == ComponentActivity.RESULT_OK) {
                    // There are no request codes
//                    val data = result.data
//                    val bitmap = data?.data
                    println("MROEBUCK: YAY!!")
                } else {
                    println("MROEBUCK: NEY!!")
                }
                println("MROEBUCK: result=[$result]")
            }
        })
//        ActivityCompat.requestPermissions(
//            activity,
//            arrayOf(permission),
//            identifier
//        )
    }

    override fun checkSelfPermission(activity: Activity, permission: String): Int {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        )
    }

    override fun canRequestManageExternalStoragePermissions(
        activity: Activity,
        currentPermission: AccessPermissionType
    ): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && currentPermission == AccessPermissionType.ManageExternalStorage
                && !isExternalStorageManager(activity)
    }

    override fun canAccessWriteExternalStorage(currentPermission: AccessPermissionType): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && currentPermission == AccessPermissionType.AccessWriteExternalStorage
    }

    override fun canCheckNormalPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    private fun isExternalStorageManager(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result =
                ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            val result1 =
                ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
        }
    }
}