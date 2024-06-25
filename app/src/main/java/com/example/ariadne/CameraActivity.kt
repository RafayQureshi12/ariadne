package com.example.ariadne

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CameraActivity : AppCompatActivity(), SurfaceHolder.Callback, TextToSpeech.OnInitListener {

    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var imageView: ImageView
    private var camera: Camera? = null
    private lateinit var textToSpeech: TextToSpeech

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        imageView = findViewById(R.id.imageView)

        client = getCustomOkHttpClient(this)
        textToSpeech = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }

    private fun openCamera() {
        textToSpeech.speak("Press the volume key to detect environment", TextToSpeech.QUEUE_ADD, null, "")

        val cameraId = getBackCameraId()
        if (cameraId != -1) {
            camera = Camera.open(cameraId)
            try {
                camera?.setPreviewDisplay(surfaceHolder)
                camera?.startPreview()
            } catch (e: IOException) {
                e.printStackTrace()
                camera?.release()
                camera = null
            }
        } else {
            Toast.makeText(this, "No back camera found", Toast.LENGTH_LONG).show()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        openCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        camera?.parameters?.let { parameters ->
            camera?.parameters = parameters
            camera?.startPreview()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    private fun handlePictureTaken(data: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val rotatedBitmap = rotateBitmap(bitmap, 90)  // Adjust orientation if needed
        imageView.setImageBitmap(rotatedBitmap)
        imageView.visibility = View.VISIBLE
        uploadImage(rotatedBitmap)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun uploadImage(bitmap: Bitmap) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)

        val json = JSONObject()
        json.put("image", imageBase64)

        Thread {
            try {
                val responseTextString = post("https://192.168.51.249:5000/api/", json.toString())
                runOnUiThread {
                    Toast.makeText(this, responseTextString, Toast.LENGTH_LONG).show()
                    textToSpeech.speak(responseTextString, TextToSpeech.QUEUE_FLUSH, null, "")
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
                    textToSpeech.speak("Failed to upload image", TextToSpeech.QUEUE_FLUSH, null, "")
                }
            }
        }.start()
    }

    private fun post(url: String, json: String): String {
        val body = RequestBody.create(JSON, json)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    private fun getCustomOkHttpClient(context: Context): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun getBackCameraId(): Int {
        val ci = CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)
            if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i
        }
        return -1
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            camera?.takePicture(null, null, Camera.PictureCallback { data, _ ->
                handlePictureTaken(data)
            })
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
    }
}
