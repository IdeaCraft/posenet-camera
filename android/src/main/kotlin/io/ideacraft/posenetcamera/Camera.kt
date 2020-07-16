package io.ideacraft.posenetcamera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.CamcorderProfile
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.roundToInt


class Camera(
        activity: Activity?,
        flutterTexture: SurfaceTextureEntry,
        dartMessenger: DartMessenger,
        cameraName: String?,
        resolutionPreset: String?,
        posenetChannel: PosenetChannel) {
    private val MODEL_WIDTH = 257
    private val MODEL_HEIGHT = 257
    private var activity: Activity? = null
    private val lock = Any()
    private var runClassifier = false
    val flutterTexture: SurfaceTextureEntry
    private val cameraManager: CameraManager
    private val orientationEventListener: OrientationEventListener
    private val isFrontFacing: Boolean
    private val sensorOrientation: Int
    private val cameraName: String?
    private val captureSize: Size
    private val previewSize: Size?
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var pictureImageReader: ImageReader? = null
    private val dartMessenger: DartMessenger
    private val posenetChannel: PosenetChannel
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private val recordingProfile: CamcorderProfile?
    private var currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN

    private val HANDLE_THREAD_NAME = "CameraBackground"

    /** An additional thread for running tasks that shouldn't block the UI. */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background. */
    private var backgroundHandler: Handler? = null

    /** A [Handler] for sending pose data to dart using main thread */
    private val uiThreadHandler: Handler = Handler(Looper.getMainLooper())

    /** An object for the Posenet library. */
    private lateinit var posenet: Posenet

    /** An HashMap to store Person */
    private lateinit var person: HashMap<String, Any>

    // Mirrors camera.dart
    enum class ResolutionPreset {
        low, medium, high, veryHigh, ultraHigh, max
    }

    @SuppressLint("MissingPermission")
    @Throws(CameraAccessException::class)
    fun open(result: MethodChannel.Result) {
        pictureImageReader = ImageReader.newInstance(
                previewSize!!.width, previewSize.height, ImageFormat.JPEG, 2)

        posenet = Posenet(activity!!)

        cameraManager.openCamera(
                cameraName,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraDevice = device
                        try {
                            startPreview()

                            // create background thread and run inference
                            backgroundThread = HandlerThread(HANDLE_THREAD_NAME)
                            backgroundThread!!.start()
                            backgroundHandler = Handler(backgroundThread!!.looper)
                            runClassifier = true

                            startBackgroundThread(periodicClassify)

                        } catch (e: CameraAccessException) {
                            result.error("CameraAccess", e.message, "Error opening the Camera.")
                            close()
                            return
                        }
                        val reply: MutableMap<String, Any> = HashMap()
                        reply["textureId"] = flutterTexture.id()
                        reply["previewWidth"] = captureSize.width
                        reply["previewHeight"] = captureSize.height
                        result.success(reply)
                    }

                    override fun onClosed(camera: CameraDevice) {
                        dartMessenger.sendCameraClosingEvent()
                        super.onClosed(camera)
                    }

                    override fun onDisconnected(cameraDevice: CameraDevice) {
                        close()
                        dartMessenger.send(DartMessenger.EventType.ERROR, "The camera was disconnected.")
                    }

                    override fun onError(cameraDevice: CameraDevice, errorCode: Int) {
                        close()
                        val errorDescription: String = when (errorCode) {
                            ERROR_CAMERA_IN_USE -> "The camera device is in use already."
                            ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                            ERROR_CAMERA_DISABLED -> "The camera device could not be opened due to a device policy."
                            ERROR_CAMERA_DEVICE -> "The camera device has encountered a fatal error"
                            ERROR_CAMERA_SERVICE -> "The camera service has encountered a fatal error."
                            else -> "Unknown camera error"
                        }
                        dartMessenger.send(DartMessenger.EventType.ERROR, errorDescription)
                    }
                },
                null)
    }

    /** Starts a background thread and its [Handler]. */
    @Synchronized
    protected fun startBackgroundThread(r: Runnable) {
        if (backgroundHandler != null) {
            backgroundHandler!!.post(r)
        }
    }

    /** Stops the background thread and its [Handler]. */
    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
            synchronized(lock) {
                runClassifier = false
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted when stopping background thread", e)
        }
    }

    /** Takes photos and classify them periodically. */
    private val periodicClassify = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (runClassifier) {
                    classifyFrame()
                }
            }
            backgroundHandler!!.post(this)
        }
    }

    /** Crop Bitmap to maintain aspect ratio of model input.   */
    private fun cropBitmap(bitmap: Bitmap): Bitmap {
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = MODEL_HEIGHT.toFloat() / MODEL_WIDTH
        var croppedBitmap = bitmap

        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        val maxDifference = 1e-5

        // Checks if the bitmap has similar aspect ratio as the required model input.
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> return croppedBitmap
            modelInputRatio < bitmapRatio -> {
                // New image is taller so we are height constrained.
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0,
                        (cropHeight / 2).toInt(),
                        bitmap.width,
                        (bitmap.height - cropHeight).toInt()
                )
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        (cropWidth / 2).toInt(),
                        0,
                        (bitmap.width - cropWidth).toInt(),
                        bitmap.height
                )
            }
        }
        return croppedBitmap
    }

    /** Runs [estimateSinglePose)] from posenet */
    private fun performInference(bitmap: Bitmap): HashMap<String, Any> {
        // Crop bitmap
//        val croppedBitmap = cropBitmap(bitmap)

        // Created scaled version of bitmap for mode input
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_WIDTH, MODEL_HEIGHT, true)

        // Return inference
        return posenet.estimateSinglePose(scaledBitmap)
    }

    /** Classifies a frame from the preview stream. */
    private fun classifyFrame() {
        val startTime =   System.nanoTime()
        if ( cameraDevice == null) {
            Log.d(TAG, "=> No ClassifyFrame")
            return
        }

        val image = pictureImageReader!!.acquireLatestImage() ?: return

        val planes = image.planes
        val bBuffer: ByteBuffer = planes[0].buffer
        bBuffer.rewind()
        val buffer = ByteArray(bBuffer.remaining())
        planes[0].buffer.get(buffer)
        val imageBitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)

        // Create rotated version for portrait display
        val rotateMatrix = Matrix()
        if(isFrontFacing) {
            rotateMatrix.postRotate(270.0f)
        } else {
            rotateMatrix.postRotate(90.0f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
                imageBitmap, 0, 0, imageBitmap.width, imageBitmap.height,
                rotateMatrix, true
        )

        image.close()

        // Perform inference
        person = performInference(rotatedBitmap)

        uiThreadHandler.post(
            Runnable {
                posenetChannel.send(person)
            }
        )

        imageBitmap.recycle()

        Log.d("Measure", "=> Inference took : " +  ((System.nanoTime()-startTime)/1000000)+ "ms\n")
    }

    @Throws(CameraAccessException::class)
    private fun createCaptureSession(templateType: Int, vararg surfaces: Surface) {
        createCaptureSession(templateType, null, *surfaces)
    }

    @Throws(CameraAccessException::class)
    private fun createCaptureSession(
            templateType: Int, onSuccessCallback: Runnable?, vararg surfaces: Surface) {
        // Close any existing capture session.
        closeCaptureSession()

        // Create a new capture builder.
        captureRequestBuilder = cameraDevice!!.createCaptureRequest(templateType)
        try {
            // Auto focus should be continuous for camera preview.
            captureRequestBuilder!!.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to set up captureRequestBuilder", e)
        }

        // Build Flutter surface to render to
        val surfaceTexture = flutterTexture.surfaceTexture()
        surfaceTexture.setDefaultBufferSize(captureSize!!.width, captureSize.height)
        val flutterSurface = Surface(surfaceTexture)
        captureRequestBuilder?.let { captureRequestBuilder!!.addTarget(flutterSurface)}

        val remainingSurfaces = listOf(*surfaces)
        for (surface in remainingSurfaces) {
            captureRequestBuilder?.let { captureRequestBuilder!!.addTarget(surface)}
        }

        // Prepare the callback
        val callback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    if (cameraDevice == null) {
                        dartMessenger.send(
                                DartMessenger.EventType.ERROR, "The camera was closed during configuration.")
                        return
                    }
                    cameraCaptureSession = session
                    captureRequestBuilder?.let { captureRequestBuilder!!.set(
                            CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO
                    )}

                    cameraCaptureSession!!.setRepeatingRequest(captureRequestBuilder!!.build(), null, null)
                    onSuccessCallback?.run()
                } catch (e: CameraAccessException) {
                    dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                } catch (e: IllegalStateException) {
                    dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                } catch (e: IllegalArgumentException) {
                    dartMessenger.send(DartMessenger.EventType.ERROR, e.message)
                }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                dartMessenger.send(
                        DartMessenger.EventType.ERROR, "Failed to configure camera session.")
            }
        }

        // Collect all surfaces we want to render to.
        val surfaceList: MutableList<Surface> = ArrayList()
        surfaceList.add(flutterSurface)
        surfaceList.addAll(remainingSurfaces)

        // Start the session
        cameraDevice!!.createCaptureSession(surfaceList, callback, null)
    }

    @Throws(CameraAccessException::class)
    fun startPreview() {
        createCaptureSession(CameraDevice.TEMPLATE_PREVIEW, pictureImageReader!!.surface)
    }

    private fun closeCaptureSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession!!.close()
            cameraCaptureSession = null
        }
        if (backgroundThread != null) {
            stopBackgroundThread()
        }
    }

    fun close() {
        closeCaptureSession()
        posenet.close()

        if (cameraDevice != null) {
            cameraDevice!!.close()
            cameraDevice = null
        }
        if (pictureImageReader != null) {
            pictureImageReader!!.close()
            pictureImageReader = null
        }
    }

    fun dispose() {
        close()
        flutterTexture.release()
        orientationEventListener.disable()
    }

    private val mediaOrientation: Int
        get() {
            val sensorOrientationOffset = if (currentOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) 0 else if (isFrontFacing) -currentOrientation else currentOrientation
            return (sensorOrientationOffset + sensorOrientation + 360) % 360
        }

    init {
        checkNotNull(activity) { "No activity available!" }
        this.activity = activity
        this.cameraName = cameraName
        this.flutterTexture = flutterTexture
        this.dartMessenger = dartMessenger
        this.posenetChannel = posenetChannel
        cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        orientationEventListener = object : OrientationEventListener(activity.applicationContext) {
            override fun onOrientationChanged(i: Int) {
                if (i == ORIENTATION_UNKNOWN) {
                    return
                }
                // Convert the raw deg angle to the nearest multiple of 90.
                currentOrientation = (i / 90.0).roundToInt() * 90
            }
        }
        orientationEventListener.enable()
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        isFrontFacing = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
        val preset = ResolutionPreset.valueOf(resolutionPreset!!)
        recordingProfile = CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(cameraName, preset)
        captureSize = Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight)
        previewSize = CameraUtils.computeBestPreviewSize(cameraName, preset)
    }
}