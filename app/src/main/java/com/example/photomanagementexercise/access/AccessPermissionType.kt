package com.example.photomanagementexercise.access

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

enum class AccessPermissionType(val identifier: Int,
                                val permission: String) {
    AccessCoarseLocation(
        0x101, Manifest.permission.ACCESS_COARSE_LOCATION),
    AccessFineLocation(
        0x102, Manifest.permission.ACCESS_FINE_LOCATION),
    AccessNetworkState(
        0x103, Manifest.permission.ACCESS_NETWORK_STATE),
    @RequiresApi(Build.VERSION_CODES.M)
    AccessNotificationPolicy(
        0x104, Manifest.permission.ACCESS_NOTIFICATION_POLICY),
    AccessWifiState(
        0x105, Manifest.permission.ACCESS_WIFI_STATE),
    AccessCamera(
        0x106, Manifest.permission.CAMERA),
    AccessInternet(
        0x107, Manifest.permission.INTERNET),
    AccessReadExternalStorage(
        0x108, Manifest.permission.READ_EXTERNAL_STORAGE),
    AccessAudioRecording(
        0x109, Manifest.permission.RECORD_AUDIO),
    AccessContacts(
        0x10A, Manifest.permission.READ_CONTACTS),
    AccessWriteExternalStorage(
        0x10B, Manifest.permission.WRITE_EXTERNAL_STORAGE),
    ManageExternalStorage(
        0x10C, Manifest.permission.MANAGE_EXTERNAL_STORAGE);

    fun next(): AccessPermissionType? {
        return if (ordinal == values().size - 1) null else values()[ordinal + 1]
    }

    companion object {
        fun valueOf(value: Int): AccessPermissionType {
            values().map {
                if(value == it.identifier) {
                    return it
                }
            }
            throw Throwable("Undefined access permission type [$value]")
        }
        fun valueOf(value: String): AccessPermissionType {
            values().map {
                if(value == it.permission) {
                    return it
                }
            }
            throw Throwable("Undefined access permission type [$value]")
        }
    }
}