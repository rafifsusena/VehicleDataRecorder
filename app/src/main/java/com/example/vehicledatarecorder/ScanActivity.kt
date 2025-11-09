package com.example.vehicledatarecorder

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vehicledatarecorder.databinding.ActivityScanBinding
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ScanActivity : AppCompatActivity() {

    private lateinit var usbMonitor: USBMonitor
    private var cameraHandler: CameraHandler? = null
    private var cameraSurface: Surface? = null

    private lateinit var textureView: TextureView
    private lateinit var flashOverlay: View
    private lateinit var btnCapture: Button
    private lateinit var binding: ActivityScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        textureView = findViewById(R.id.textureView)
        flashOverlay = findViewById(R.id.flashOverlay)
        btnCapture = findViewById(R.id.captureButton)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textureView.keepScreenOn = true

        usbMonitor = USBMonitor(this, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                Toast.makeText(this@ScanActivity, "USB Camera Attached", Toast.LENGTH_SHORT).show()
                device?.let { usbMonitor.requestPermission(it) }
            }

            override fun onDettach(device: UsbDevice?) {
                Toast.makeText(this@ScanActivity, "USB Camera Detached", Toast.LENGTH_SHORT).show()
                releaseCamera()
            }

            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
                createNew: Boolean
            ) {
                releaseCamera()
                if (ctrlBlock != null) {
                    cameraHandler = CameraHandler()
                    cameraHandler?.open(ctrlBlock)
                    openCameraPreview()
                }
            }

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                releaseCamera()
            }

            override fun onCancel(device: UsbDevice?) {}
        })

        btnCapture.setOnClickListener {
            captureImage()
        }
    }

    override fun onStart() {
        super.onStart()
        usbMonitor.register()
    }

    override fun onStop() {
        super.onStop()
        usbMonitor.unregister()
        releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCamera()
        usbMonitor.destroy()
    }

    private fun openCameraPreview() {
        val texture = textureView.surfaceTexture
        if (texture != null) {
            cameraSurface = Surface(texture)
            cameraHandler?.startPreview(cameraSurface!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    cameraSurface = Surface(surface)
                    cameraHandler?.startPreview(cameraSurface!!)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    cameraSurface?.release()
                    cameraSurface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun captureImage() {
        cameraHandler?.captureStillImage(binding.textureView) { bitmap ->
            runOnUiThread {
                showFlashOverlay()

                lifecycleScope.launch(Dispatchers.IO) {
                    saveBitmapToStorage(bitmap)
                }
            }
        }
    }


    private fun showFlashOverlay() {
        flashOverlay.visibility = View.VISIBLE
        flashOverlay.postDelayed({
            flashOverlay.visibility = View.GONE
        }, 150)
    }

    private fun saveBitmapToStorage(bitmap: Bitmap): String? {
        return try {
            val dir = File(getExternalFilesDir(null), "captures")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun releaseCamera() {
        cameraHandler?.release()
        cameraHandler = null
        cameraSurface?.release()
        cameraSurface = null
    }
}
