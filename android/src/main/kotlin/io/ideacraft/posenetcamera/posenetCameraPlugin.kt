package io.ideacraft.posenetcamera

import android.app.Activity
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import io.flutter.view.TextureRegistry
import io.ideacraft.posenetcamera.CameraPermissions.PermissionsRegistry

/** PosenetCameraPlugin  */
class PosenetCameraPlugin : FlutterPlugin, ActivityAware {
    private var flutterPluginBinding: FlutterPluginBinding? = null
    private var methodCallHandler: MethodCallHandlerImpl? = null
    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        flutterPluginBinding = binding
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        flutterPluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        maybeStartListening(
                binding.activity,
                flutterPluginBinding!!.binaryMessenger, object : PermissionsRegistry {
            override fun addListener(handler: RequestPermissionsResultListener?) {
                binding.addRequestPermissionsResultListener(handler!!)
            }
        },
                flutterPluginBinding!!.textureRegistry)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        if (methodCallHandler == null) {
            // Could be on too low of an SDK to have started listening originally.
            return
        }
        methodCallHandler!!.stopListening()
        methodCallHandler = null
    }
    
    private fun maybeStartListening(
            activity: Activity,
            messenger: BinaryMessenger,
            permissionsRegistry: PermissionsRegistry,
            textureRegistry: TextureRegistry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If the sdk is less than 21 (min sdk for Camera2) we don't register the plugin.
            return
        }
        methodCallHandler = MethodCallHandlerImpl(
                activity, messenger, CameraPermissions(), permissionsRegistry, textureRegistry)
    }

    companion object {
        private const val TAG = "posenetCameraPlugin"
        fun registerWith(registrar: Registrar) {
            val plugin = PosenetCameraPlugin()
            plugin.maybeStartListening(
                    registrar.activity(),
                    registrar.messenger(), object : PermissionsRegistry {
                override fun addListener(handler: RequestPermissionsResultListener?) {
                    registrar.addRequestPermissionsResultListener(handler)
                }
            },
                    registrar.view())
        }
    }
}