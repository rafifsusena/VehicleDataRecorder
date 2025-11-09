package com.example.vehicledatarecorder

import android.graphics.Bitmap
import android.view.Surface
import android.view.TextureView
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.USBMonitor

class CameraHandler {

    private var uvcCamera: UVCCamera? = null

    fun open(ctrlBlock: USBMonitor.UsbControlBlock) {
        release()
        uvcCamera = UVCCamera().apply {
            open(ctrlBlock)
            setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
        }
    }

    fun startPreview(surface: Surface) {
        uvcCamera?.setPreviewDisplay(surface)
        uvcCamera?.startPreview()
    }

    fun stopPreview() {
        uvcCamera?.stopPreview()
    }

    /**
     * Capture still image by grabbing a Bitmap from TextureView
     */
    fun captureStillImage(textureView: TextureView, callback: (Bitmap) -> Unit) {
        try {
            val bitmap = textureView.bitmap
            if (bitmap != null) {
                callback(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            uvcCamera?.destroy()
        } catch (_: Exception) {
        }
        uvcCamera = null
    }
}
