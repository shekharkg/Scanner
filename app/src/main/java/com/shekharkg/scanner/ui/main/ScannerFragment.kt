package com.shekharkg.scanner.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.shekharkg.scanner.R
import com.shekharkg.scanner.factory.ViewModelFactory
import com.shekharkg.scanner.model.State
import com.shekharkg.scanner.utils.CombinedCaptureResult
import kotlinx.android.synthetic.main.fragment_scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private const val IMAGE_BUFFER_SIZE: Int = 3
private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

class ScannerFragment : Fragment() {

    private lateinit var viewModel: SharedViewModel
    private lateinit var camera: CameraDevice

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private lateinit var imageReader: ImageReader
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private lateinit var session: CameraCaptureSession
    lateinit var characteristics: CameraCharacteristics
    private val animationTask: Runnable by lazy {
        Runnable {
            surfaceView.background = Color.argb(150, 255, 255, 255).toDrawable()
        }
    }

    companion object {
        private val TAG = ScannerFragment::class.java.simpleName
        fun newInstance() = ScannerFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let {
            viewModel = ViewModelProvider(it, ViewModelFactory())[SharedViewModel::class.java]
        }

        val cameraId = getBackCameraId(cameraManager)
        if (cameraId != null) {
            characteristics = cameraManager.getCameraCharacteristics(cameraId)
            setupCameraView(cameraId)
        } else {
            Toast.makeText(activity, "Unable to get Camera", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCameraView(cameraId: String) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                view?.post { startCamera(cameraId) }
            }
        })
    }

    private fun startCamera(cameraId: String) = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!
            .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
        imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.JPEG, IMAGE_BUFFER_SIZE
        )

        val targets = listOf(surfaceView.holder.surface, imageReader.surface)
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        ).apply { addTarget(surfaceView.holder.surface) }

        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        captureImageAction.setOnClickListener {
            it.isEnabled = false

            // Perform I/O heavy operations in a different scope
            lifecycleScope.launch(Dispatchers.IO) {
                takePhoto().use { result ->

                    val croppedImage = getCroppedImageFromResult(result)

                    // Display the photo taken to user
                    lifecycleScope.launch(Dispatchers.Main) {
                        closeCamera()
                        quitThreads()

                        viewModel.setImagePath(croppedImage)
                        viewModel.setState(State.IMAGE_CAPTURED)
                    }

                }

            }
        }
    }

    private fun getCroppedImageFromResult(result: CombinedCaptureResult): Bitmap {
        val buffer = result.image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        val capturedImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val padding = imageFrame.left
        val cropWidth = capturedImage.width - (padding * 2)
        val cropHeight = capturedImage.height - (padding * 2)

        return Bitmap.createBitmap(
            capturedImage,
            padding,
            padding,
            cropWidth,
            cropHeight
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    private fun getBackCameraId(cameraManager: CameraManager): String? {
        val availableCameras: HashMap<String, FloatArray> = HashMap()

        val cameraIds = cameraManager.cameraIdList

        for (cameraId in cameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                val focalLength = characteristics.get(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                ) ?: floatArrayOf(0F)
                availableCameras[cameraId] = focalLength
            }
        }

        var selectedCameraId: String? = null
        var focalLength: Float? = null

        for (camera in availableCameras.entries) {
            if (focalLength == null || selectedCameraId == null) {
                selectedCameraId = camera.key
                focalLength = camera.value.maxOrNull()
            } else {
                val tempFocalLength = camera.value.maxOrNull() ?: 0F
                if (tempFocalLength < focalLength) {
                    focalLength = tempFocalLength
                    selectedCameraId = camera.key
                }
            }
        }

        return selectedCameraId
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun takePhoto():
            CombinedCaptureResult = suspendCoroutine { cont ->


        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE
        ).apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                surfaceView.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)

                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp
                        ) continue

                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        val exifOrientation = ExifInterface.ORIENTATION_ROTATE_90

                        cont.resume(
                            CombinedCaptureResult(
                                image, result, exifOrientation, imageReader.imageFormat
                            )
                        )

                    }
                }
            }
        }, cameraHandler)
    }

    private fun closeCamera(){
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    private fun quitThreads(){
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    override fun onStop() {
        super.onStop()
        closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        quitThreads()
    }
}