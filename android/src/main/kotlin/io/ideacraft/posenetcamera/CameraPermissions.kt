package io.ideacraft.posenetcamera

import android.Manifest.permission
import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener

internal class CameraPermissions {
    internal interface PermissionsRegistry {
        fun addListener(handler: RequestPermissionsResultListener?)
    }

    internal interface ResultCallback {
        fun onResult(errorCode: String?, errorDescription: String?)
    }

    private var ongoing = false
    fun requestPermissions(
            activity: Activity,
            permissionsRegistry: PermissionsRegistry,
            callback: ResultCallback) {
        if (ongoing) {
            callback.onResult("cameraPermission", "Camera permission request ongoing")
        }
        if (!hasCameraPermission(activity)) {
            permissionsRegistry.addListener(
                    CameraRequestPermissionsListener(
                            object : ResultCallback {
                                override fun onResult(errorCode: String?, errorDescription: String?) {
                                    ongoing = false
                                    callback.onResult(errorCode, errorDescription)
                                }
                            }))
            ongoing = true
            ActivityCompat.requestPermissions(
                    activity, arrayOf(permission.CAMERA),
                    CAMERA_REQUEST_ID)
        } else {
            // Permissions already exist. Call the callback with success.
            callback.onResult(null, null)
        }
    }

    private fun hasCameraPermission(activity: Activity): Boolean {
        return (ContextCompat.checkSelfPermission(activity, permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)
    }

    @VisibleForTesting
    internal class CameraRequestPermissionsListener @VisibleForTesting constructor(private val callback: ResultCallback) : RequestPermissionsResultListener {
        // There's no way to unregister permission listeners in the v1 embedding, so we'll be called
        // duplicate times in cases where the user denies and then grants a permission. Keep track of if
        // we've responded before and bail out of handling the callback manually if this is a repeat
        // call.
        var alreadyCalled = false
        override fun onRequestPermissionsResult(id: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
            if (alreadyCalled || id != CAMERA_REQUEST_ID) {
                return false
            }
            alreadyCalled = true
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                callback.onResult("cameraPermission", "Camera permission not granted")
            } else {
                callback.onResult(null, null)
            }
            return true
        }

    }

    companion object {
        private const val CAMERA_REQUEST_ID = 9796
    }
}