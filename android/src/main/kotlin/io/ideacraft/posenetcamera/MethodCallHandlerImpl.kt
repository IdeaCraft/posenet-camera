package io.ideacraft.posenetcamera

import android.app.Activity
import android.hardware.camera2.CameraAccessException
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.view.TextureRegistry
import io.ideacraft.posenetcamera.CameraPermissions.PermissionsRegistry
import io.ideacraft.posenetcamera.CameraPermissions.ResultCallback

internal class MethodCallHandlerImpl(
        private val activity: Activity,
        private val messenger: BinaryMessenger,
        private val cameraPermissions: CameraPermissions,
        private val permissionsRegistry: PermissionsRegistry,
        private val textureRegistry: TextureRegistry) : MethodCallHandler {
    private val methodChannel: MethodChannel = MethodChannel(messenger, "posenetCameraPlugin/posenetCamera")

    private var camera: Camera? = null
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "availableCameras" -> try {
                result.success(CameraUtils.getAvailableCameras(activity))
            } catch (e: Exception) {
                handleException(e, result)
            }
            "initialize" -> {
                if (camera != null) {
                    camera!!.close()
                }
                cameraPermissions.requestPermissions(
                    activity,
                    permissionsRegistry,
                    object : ResultCallback {
                        override fun onResult(errorCode: String?, errorDescription: String?) {
                            if (errorCode == null) {
                                try {
                                    instantiateCamera(call, result)
                                } catch (e: Exception) {
                                    handleException(e, result)
                                }
                            } else {
                                result.error(errorCode, errorDescription, null)
                            }
                        }
                    }
                )
            }
            "dispose" -> {
                if (camera != null) {
                    camera!!.dispose()
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
    }

    @Throws(CameraAccessException::class)
    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
        val cameraName = call.argument<String>("cameraName")
        val resolutionPreset = call.argument<String>("resolutionPreset")
        val flutterSurfaceTexture = textureRegistry.createSurfaceTexture()
        val dartMessenger = DartMessenger(messenger, flutterSurfaceTexture.id())
        val posenetChannel = PosenetChannel(messenger)

        camera = Camera(
                activity,
                flutterSurfaceTexture,
                dartMessenger,
                cameraName,
                resolutionPreset,
                posenetChannel)

        camera!!.open(result)
    }

    private fun handleException(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
        }
        throw (exception as RuntimeException)
    }

    init {
        methodChannel.setMethodCallHandler(this)
    }
}