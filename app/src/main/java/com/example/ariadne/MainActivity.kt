package com.example.ariadne

import okhttp3.MediaType.Companion.toMediaType
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.*
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.*
import ai.picovoice.porcupine.*;

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var responseText: TextView
    private lateinit var textToSpeech: TextToSpeech

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private lateinit var client: OkHttpClient

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

    private fun speakResponse(message: String) {
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize custom OkHttpClient
        client = getCustomOkHttpClient(this)
        textToSpeech = TextToSpeech(this, this)

        val buttonTakePicture = findViewById<Button>(R.id.button_take_picture)
        val buttonUploadImage = findViewById<Button>(R.id.button_upload)
        responseText = findViewById(R.id.response_text)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),100)
        buttonTakePicture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
            } else {
                openCamera()
            }
        }

        buttonUploadImage.setOnClickListener {
            openGallery()
        }
    }

    private fun openCamera() {
//        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        startActivityForResult(takePictureIntent, 101)
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, 102)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                101 -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    uploadImage(imageBitmap)
                }
                102 -> {
                    val selectedImage = data?.data
                    val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImage)
                    uploadImage(imageBitmap)
                }
            }
        }
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
                val responseTextString =  post("https://192.168.51.249:5000/api/", json.toString())
                runOnUiThread {
                    responseText.text = responseTextString
                    speakResponse(responseTextString)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    responseText.text = "Failed to upload image: ${e.message}"
                    speakResponse("Failed to upload image")
                }
            }
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for Text-to-Speech
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Language data is missing or the language is not supported
                // Handle the error
            } else {
                // Only speak when Text-to-Speech is successfully initialized
//                speakResponse("Hello! How may I help you?")
            }
        } else {
            // Text-to-Speech initialization failed
            // Handle the error
        }
    }

    private fun getCustomOkHttpClient(context: Context): OkHttpClient {
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return arrayOf()
                }
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down Text-to-Speech when the activity is destroyed to release resources
        textToSpeech.shutdown()
    }
}
