package com.cityray.edge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EdgeCameraActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private lateinit var cameraList: LinearLayout
    private lateinit var recordButton: Button

    private var cameras: List<EdgeCameraInfo> = emptyList()
    private var selectedCameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var previewSize: Size = Size(1280, 720)
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        buildUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            scanCameras()
        }
    }

    override fun onResume() {
        super.onResume()
        cameraThread = HandlerThread("EdgeCameraThread").apply { start() }
        cameraHandler = Handler(cameraThread.looper)
        if (::textureView.isInitialized && textureView.isAvailable && selectedCameraId != null && !isRecording) {
            startPreview(selectedCameraId.orEmpty())
        }
    }

    override fun onPause() {
        stopRecordingSafely()
        closeCamera()
        if (::cameraThread.isInitialized) cameraThread.quitSafely()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            scanCameras()
        } else {
            setStatus("Camera permission is required to preview vehicle cameras.")
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "EDGE CAMERA"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "Camera2 preview and optional silent recording for exposed HU camera streams."
            setTextColor(0xFFA7B0C0.toInt())
            textSize = 16f
            setPadding(0, dp(2), 0, dp(12))
        })

        textureView = TextureView(this).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    selectedCameraId?.let { startPreview(it) }
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
            background = getDrawable(R.drawable.panel_background)
        }
        root.addView(FrameLayout(this).apply {
            background = getDrawable(R.drawable.widget_background)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(textureView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(10))
        })

        statusText = TextView(this).apply {
            setTextColor(0xFFDDE6F3.toInt())
            textSize = 16f
            setPadding(dp(8), dp(6), dp(8), dp(6))
            text = "Scanning cameras..."
        }
        root.addView(statusText, fullWidth())

        cameraList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(cameraList, fullWidth())

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bigButton("Start Preview") {
                val id = selectedCameraId
                if (id == null) setStatus("Select a camera first.") else startPreview(id)
            }, rowWeight())
            recordButton = bigButton("Record") { toggleRecording() }
            addView(recordButton, rowWeight())
        })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bigButton("Rescan") { scanCameras() }, rowWeight())
            addView(bigButton("Open Archive") { showLatestRecording() }, rowWeight())
            addView(bigButton("‹") { finish() }, rowWeight())
        })

        setContentView(wrap(root))
    }

    private fun scanCameras() {
        cameras = runCatching {
            cameraManager.cameraIdList.map { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
                EdgeCameraInfo(
                    id = id,
                    label = labelForCamera(id, facing),
                    detail = cameraDetail(characteristics, sizes),
                    previewSizes = sizes
                )
            }
        }.getOrElse {
            setStatus("Camera scan failed: ${it.message ?: "unknown error"}")
            emptyList()
        }
        selectedCameraId = selectedCameraId ?: cameras.firstOrNull { it.label.contains("Rear", ignoreCase = true) }?.id ?: cameras.firstOrNull()?.id
        renderCameraList()
        if (cameras.isEmpty()) {
            setStatus("No Camera2 cameras are exposed to EDGE on this head unit.")
        } else {
            setStatus("Found ${cameras.size} camera stream(s). Select one and tap Start Preview.")
            if (textureView.isAvailable) selectedCameraId?.let { startPreview(it) }
        }
    }

    private fun renderCameraList() {
        cameraList.removeAllViews()
        cameras.forEach { info ->
            val selected = info.id == selectedCameraId
            cameraList.addView(Button(this).apply {
                text = "${if (selected) "● " else ""}${info.label}\n${info.detail}"
                textSize = 16f
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.WHITE)
                background = getDrawable(R.drawable.tile_button_background)
                setOnClickListener {
                    selectedCameraId = info.id
                    renderCameraList()
                    startPreview(info.id)
                }
            }, fullWidth())
        }
    }

    private fun startPreview(cameraId: String) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        if (!textureView.isAvailable) {
            setStatus("Preview surface is not ready yet.")
            return
        }
        ensureCameraThread()
        stopRecordingSafely()
        closeCamera()
        val cameraInfo = cameras.firstOrNull { it.id == cameraId }
        previewSize = chooseSize(cameraInfo?.previewSizes.orEmpty())
        runCatching {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    setStatus("Camera disconnected.")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    setStatus("Camera open failed. Error $error.")
                }
            }, cameraHandler)
        }.onFailure {
            setStatus("Camera open blocked: ${it.message ?: "not accessible"}")
        }
    }

    private fun createPreviewSession(camera: CameraDevice) {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }
        camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                runCatching {
                    session.setRepeatingRequest(request.build(), null, cameraHandler)
                    setStatus("Preview active: ${cameraLabel(selectedCameraId.orEmpty())}")
                }.onFailure {
                    setStatus("Preview failed: ${it.message ?: "unknown error"}")
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                setStatus("Camera preview configuration failed.")
            }
        }, cameraHandler)
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecordingSafely()
            selectedCameraId?.let { startPreview(it) }
        } else {
            val id = selectedCameraId
            if (id == null) {
                setStatus("Select a camera first.")
            } else {
                startRecording(id)
            }
        }
    }

    private fun startRecording(cameraId: String) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        if (!textureView.isAvailable) {
            setStatus("Preview surface is not ready yet.")
            return
        }
        ensureCameraThread()
        closeCamera()
        val cameraInfo = cameras.firstOrNull { it.id == cameraId }
        previewSize = chooseSize(cameraInfo?.previewSizes.orEmpty())
        val file = recordingFile()
        recordingFile = file
        val recorder = runCatching {
            MediaRecorder().apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(6_000_000)
                setVideoFrameRate(30)
                setVideoSize(previewSize.width, previewSize.height)
                setOutputFile(file.absolutePath)
                prepare()
            }
        }.getOrElse {
            setStatus("Recorder setup failed: ${it.message ?: "unknown error"}")
            return
        }
        mediaRecorder = recorder
        runCatching {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createRecordingSession(camera, recorder, file)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    setStatus("Camera disconnected while recording.")
                    stopRecordingSafely()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    setStatus("Recording camera open failed. Error $error.")
                    stopRecordingSafely()
                }
            }, cameraHandler)
        }.onFailure {
            setStatus("Recording blocked: ${it.message ?: "not accessible"}")
            recorder.release()
            mediaRecorder = null
        }
    }

    private fun createRecordingSession(camera: CameraDevice, recorder: MediaRecorder, file: File) {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)
        val recordingSurface = recorder.surface
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(previewSurface)
            addTarget(recordingSurface)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }
        camera.createCaptureSession(listOf(previewSurface, recordingSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                runCatching {
                    session.setRepeatingRequest(request.build(), null, cameraHandler)
                    recorder.start()
                    isRecording = true
                    runOnUiThread {
                        recordButton.text = "Stop"
                        setStatus("Recording: ${file.name}")
                    }
                }.onFailure {
                    setStatus("Recording failed: ${it.message ?: "unknown error"}")
                    stopRecordingSafely()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                setStatus("Recording configuration failed.")
                stopRecordingSafely()
            }
        }, cameraHandler)
    }

    private fun stopRecordingSafely() {
        if (!isRecording && mediaRecorder == null) return
        runCatching { captureSession?.stopRepeating() }
        runCatching { captureSession?.abortCaptures() }
        runCatching {
            if (isRecording) mediaRecorder?.stop()
        }.onFailure {
            recordingFile?.delete()
        }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        isRecording = false
        runOnUiThread {
            if (::recordButton.isInitialized) recordButton.text = "Record"
            recordingFile?.let { setStatus("Saved: ${it.absolutePath}") }
        }
    }

    private fun closeCamera() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
    }

    private fun ensureCameraThread() {
        if (!::cameraThread.isInitialized || !cameraThread.isAlive) {
            cameraThread = HandlerThread("EdgeCameraThread").apply { start() }
            cameraHandler = Handler(cameraThread.looper)
        } else if (!::cameraHandler.isInitialized) {
            cameraHandler = Handler(cameraThread.looper)
        }
    }

    private fun chooseSize(sizes: List<Size>): Size {
        if (sizes.isEmpty()) return Size(1280, 720)
        return sizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.maxBy { it.width * it.height }
    }

    private fun labelForCamera(id: String, facing: Int?): String {
        val base = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> "Rear Camera"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External Camera"
            else -> "Camera $id"
        }
        return "$base  ID $id"
    }

    private fun cameraDetail(characteristics: CameraCharacteristics, sizes: List<Size>): String {
        val level = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "level 3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "external"
            else -> "unknown"
        }
        val best = sizes.maxByOrNull { it.width * it.height }?.let { "${it.width}x${it.height}" } ?: "no preview size"
        return "$level • best $best"
    }

    private fun cameraLabel(id: String): String = cameras.firstOrNull { it.id == id }?.label ?: "Camera $id"

    private fun recordingFile(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "EDGE_CAMERA").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "EDGE_${stamp}.mp4")
    }

    private fun showLatestRecording() {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "EDGE_CAMERA")
        val latest = dir.listFiles()?.filter { it.extension.equals("mp4", ignoreCase = true) }?.maxByOrNull { it.lastModified() }
        if (latest == null) {
            setStatus("No EDGE camera recordings yet.")
        } else {
            setStatus("Latest recording:\n${latest.absolutePath}")
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            if (::statusText.isInitialized) statusText.text = message
            Toast.makeText(this, message.lines().firstOrNull().orEmpty(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun wrap(content: View): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        addView(ImageView(this@EdgeCameraActivity).apply {
            setImageResource(R.drawable.cityray_vip_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 1f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(ScrollView(this@EdgeCameraActivity).apply {
            setBackgroundColor(0xB8030509.toInt())
            addView(content)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun bigButton(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 17f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = getDrawable(R.drawable.tile_button_background)
        minHeight = dp(84)
        setOnClickListener { click() }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, dp(5), 0, dp(5))
    }

    private fun rowWeight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(5), dp(5), dp(5), dp(5))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 44
    }
}

private data class EdgeCameraInfo(
    val id: String,
    val label: String,
    val detail: String,
    val previewSizes: List<Size>
)
