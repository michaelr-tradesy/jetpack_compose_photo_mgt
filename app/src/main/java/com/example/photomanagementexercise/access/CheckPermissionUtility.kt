package com.example.photomanagementexercise.access

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import arrow.core.Either
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * @author Coach Roebuck
 * This interface serves as to replace the {@link com.tradesy.android.service.Permission} class.
 * This class only uses RXJava components that's sufficient to actually do the job.
 * @since 2.17
 */
interface CheckPermissionUtility {
    /**
     * @author Coach Roebuck
     * This method is designed to handle the result of each permission request.
     * @param requestCode The request code.
     * @param permissions The permission at hand, preferably the AccessPermissionType.name.
     * @param grantResults THe grand result
     * @since 2.17
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    )

    /**
     * @author Coach Roebuck
     * This method will start the process of requesting a list of permissions specified.
     * @param activity The specified activity
     * @param permissions The list of permissions, of type AccessPermissionType.
     * @since 2.17
     */
    fun start(
        activity: Activity,
        permissions: MutableList<AccessPermissionType>
    )

    /**
     * @author Coach Roebuck
     * This method will continue the process of requesting permissions.
     * This is intended to be used as a back door in case Activity::onActivityResult()
     * is invoked, if the requestCode is tied to a AccessPermissionType,
     * and the resultCode == RESULT_OK.
     * This method will continue requesting  any remaining permissions.
     * @since 2.17
     */
    fun continueRequestingPermissions()

    class PermissionsDeniedThrowable(val permission: AccessPermissionType) : Throwable()
    class UserPermissionRequiredThrowable(val permission: AccessPermissionType) : Throwable()

    val state: StateFlow<Either<Throwable, Boolean>>
    fun onPermissionDenied()
    fun onPermissionAccepted()
}

/**
 * @author Coach Roebuck
 * This method serves as the default implementation of the CheckPermissionUtility interface.
 * @since 2.17
 */
class DefaultCheckPermissionUtility(
    private val CheckAccessByVersionWrapper: CheckAccessByVersionWrapper
) : CheckPermissionUtility {

    private val androidSdkVersion = "VERSION: ${Build.VERSION.SDK_INT}: ${
        when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.N -> " (7) \"Nougat\""
            Build.VERSION_CODES.O -> " (8) \"Oreo\""
            Build.VERSION_CODES.P -> " (9) \"Pie\""
            Build.VERSION_CODES.Q -> "(10) \"Quince Tart\""
            Build.VERSION_CODES.R -> "(11) \"Red Velvet Cake\""
            31 -> "(12) \"Snow Cone\""
            else -> "?"
        }
    }"

    private lateinit var activity: Activity
    private var permissions: MutableList<AccessPermissionType> = mutableListOf()
    private var currentPermission: AccessPermissionType? = null

    // Backing property to avoid state updates from other classes
    private val _state: MutableStateFlow<Either<Throwable, Boolean>> = MutableStateFlow(Either.Right(false))
    // The UI collects from this StateFlow to get its state updates
    override val state: StateFlow<Either<Throwable, Boolean>> = _state

    override fun onPermissionDenied() {
        println("MROEBUCK: Permission DENIED: [${currentPermission?.permission}]...")
        currentPermission?.identifier?.let {
            _state.value = Either.Left(
                CheckPermissionUtility.PermissionsDeniedThrowable(
                    AccessPermissionType.valueOf(it)
                )
            )
        }
    }

    override fun onPermissionAccepted() {
        println("MROEBUCK: Permission Granted: [${currentPermission?.permission}]...")
        checkNextPermission()
    }

    override fun start(activity: Activity, permissions: MutableList<AccessPermissionType>) {
        this.activity = activity
        this.permissions = permissions
        checkNextPermission()
    }

    override fun continueRequestingPermissions() {
        checkNextPermission()
    }

    @Deprecated("Use onPermissionAccepted() and onPermissionDenied() instead. The current permission is monitored and retained")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            currentPermission?.identifier -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    // permission was granted, yay! Do the task(s) you need to do.
                    println("MROEBUCK: Permission Granted: [${currentPermission?.permission}]...")
                    checkNextPermission()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    println("MROEBUCK: Permission DENIED: [${currentPermission?.permission}]...")
                    _state.value = Either.Left(
                        CheckPermissionUtility.PermissionsDeniedThrowable(
                            AccessPermissionType.valueOf(requestCode)
                        )
                    )
                }
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
                Log.e(this::class.java.simpleName, "Oh Oh! Error! expected=[$currentPermission] received=[$requestCode]")
            }
        }
    }

    private fun checkNextPermission() {
        currentPermission = if (permissions.isNotEmpty()) permissions.removeAt(0) else null

        currentPermission?.let { checkPermission(it) } ?: run {
            _state.value = Either.Right(true)
        }
    }

    private fun checkPermission(currentPermission: AccessPermissionType) {
        // Here, this is the current activity
        if (CheckAccessByVersionWrapper.checkSelfPermission(
                activity,
                currentPermission.permission
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            // Should we show an explanation?
            when {
                CheckAccessByVersionWrapper.shouldShowRequestPermissionRationale(
                    activity,
                    currentPermission.permission
                ) -> {
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                    println("MROEBUCK: Permission request: [${currentPermission.permission}]..." )
                    _state.value = Either.Left(CheckPermissionUtility.UserPermissionRequiredThrowable(currentPermission))
                }
                // No explanation needed, we can request the permission.
                CheckAccessByVersionWrapper.canRequestManageExternalStoragePermissions(
                    activity,
                    currentPermission
                ) -> {
                    println("MROEBUCK: Must request permission: [${currentPermission.permission}]...")
                    CheckAccessByVersionWrapper.requestManageExternalStoragePermissions(
                        activity,
                        currentPermission.identifier
                    )
                }
                CheckAccessByVersionWrapper.canAccessWriteExternalStorage(currentPermission) -> {
                    println("MROEBUCK: Must request permission: [${currentPermission.permission}]...")
                    CheckAccessByVersionWrapper.requestPermissions(
                        activity,
                        currentPermission.name,
                        currentPermission.identifier
                    )
                }
                CheckAccessByVersionWrapper.canCheckNormalPermission()
                        && currentPermission != AccessPermissionType.ManageExternalStorage -> {
                    // No explanation needed, we can request the permission.
                    println("MROEBUCK: Requesting permission: [${currentPermission.permission}]...")
                    CheckAccessByVersionWrapper.requestPermissions(
                        activity,
                        currentPermission.permission,
                        currentPermission.identifier
                    )
                }
                else -> {
                    Log.i(this::class.java.simpleName,"Skip permission: " + "[${currentPermission.permission}] " + androidSdkVersion)
                    checkNextPermission()
                }
            }
        } else {
            // Permission has already been granted
            Log.i(this::class.java.simpleName,"Permission Granted: [${currentPermission.permission}]...")
            checkNextPermission()
        }
    }
}

/**
 * @return PackageManager permission state as string.
 * @since 2.19
 */
fun Int.toPermissionCheckString(): String = when (this) {
    PackageManager.PERMISSION_GRANTED -> "PERMISSION_GRANTED"
    PackageManager.PERMISSION_DENIED -> "PERMISSION_DENIED"
    else -> toString()
}
