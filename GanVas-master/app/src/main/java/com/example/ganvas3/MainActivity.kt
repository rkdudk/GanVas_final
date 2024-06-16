package com.example.ganvas3

import TFLiteModel
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var monetModel: TFLiteModel
    private lateinit var photoModel: TFLiteModel

    private var selectButton: Button? = null
    private var transformButton: Button? = null
    private var captureButton: Button? = null
    private var imageView: ImageView? = null
    private var result: ImageView? = null
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 모델 초기화
        try {
            monetModel = TFLiteModel(this, "monet_generator_model.tflite", "monet_discriminator_model.tflite")
            photoModel = TFLiteModel(this, "photo_generator_model.tflite", "photo_discriminator_model.tflite")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing models", e)
            return
        }

        // 버튼 및 이미지뷰 초기화
        transformButton = findViewById(R.id.transformBtn)
        captureButton = findViewById(R.id.captureBtn)
        selectButton = findViewById(R.id.selectBtn)
        imageView = findViewById(R.id.imageview)
        result = findViewById(R.id.result)

        // transform-button 클릭 이벤트
        transformButton?.setOnClickListener {
            val image = (imageView?.drawable as? BitmapDrawable)?.bitmap
            if (image != null) {
                try {
                    val monetBitmap = Bitmap.createBitmap(image.width, image.height, image.config)
                    val photoBitmap = Bitmap.createBitmap(image.width, image.height, image.config)

                    // 1. 일반 사진 -> 모네 화풍
                    monetModel.runGenerator(image, monetBitmap)

                    // 2. 모네 화풍 -> 일반 사진
                    photoModel.runGenerator(monetBitmap, photoBitmap)

                    // 3. 최종 모네 화풍 적용
                    val finalMonetBitmap = Bitmap.createBitmap(photoBitmap.width, photoBitmap.height, photoBitmap.config)
                    monetModel.runGenerator(photoBitmap, finalMonetBitmap)

                    result?.setImageBitmap(finalMonetBitmap)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error during image transformation", e)
                }
            }
        }

        // capture-button 클릭 이벤트
        captureButton?.run {
            setOnClickListener {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(intent, 20)
            }
        }

        // select-button 클릭 이벤트
        selectButton?.run {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                startActivityForResult(intent, 10)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                10 -> {
                    val uri = data?.data
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                        imageView?.setImageBitmap(bitmap)
                    } catch (e: IOException) {
                        Log.e("MainActivity", "Error loading image from gallery", e)
                    }
                }
                20 -> {
                    val extras = data?.extras
                    if (extras != null && extras.containsKey("data")) {
                        val image = extras["data"] as Bitmap?
                        imageView?.setImageBitmap(image)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            monetModel.close()
            photoModel.close()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error closing models", e)
        }
    }
}
