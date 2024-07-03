package com.cropimage

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class CropImageModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private var promise: Promise? = null
    private var options: ReadableMap? = null
    private var currentPhotoPath: String? = null

    private val IMAGE_PICKER_REQUEST = 1
    private val IMAGE_CAPTURE_REQUEST = 2
    private val PERMISSIONS_REQUEST_CAMERA = 101

    init {
        reactContext.addActivityEventListener(object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity?,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                // Launch coroutine to handle activity result
                CoroutineScope(Dispatchers.Main).launch {
                    handleActivityResult(requestCode, resultCode, data)
                }
            }
        })
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun pickImage(promise: Promise) {
        this.promise = promise
        val pickIntent = Intent(Intent.ACTION_PICK)
        pickIntent.type = "image/*"
        currentActivity?.startActivityForResult(pickIntent, IMAGE_PICKER_REQUEST)
    }

    private fun checkCameraPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                currentActivity!!, arrayOf<String>(Manifest.permission.CAMERA),
                PERMISSIONS_REQUEST_CAMERA
            )
            return false
        }
        return true
    }

    @ReactMethod
    fun configure(options: ReadableMap) {
        this.options = options
    }

    @ReactMethod
    fun captureImage(promise: Promise) {
        this.promise = promise
        if (checkCameraPermissions()) {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (captureIntent.resolveActivity(reactApplicationContext.packageManager) != null) {
                var photoFile: File? = null
                try {
                    photoFile = createImageFile()
                } catch (ex: IOException) {
                    promise.reject("ERROR", "Failed to create image file")
                }
                if (photoFile != null) {
                    val photoURI = FileProvider.getUriForFile(
                        reactApplicationContext,
                        reactApplicationContext.packageName + ".fileprovider",
                        photoFile
                    )
                    captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    currentActivity!!.startActivityForResult(captureIntent, IMAGE_CAPTURE_REQUEST)
                } else {
                    promise.reject("ERROR", "Failed to create image file")
                }
            } else {
                promise.reject("ERROR", "No camera app found")
            }
        }
    }

    private fun createImageFile(): File? {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File =
            reactApplicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private suspend fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {

            IMAGE_PICKER_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        startCropping(uri)
                    } ?: promise?.reject("ERROR", "Failed to pick image")
                } else {
                    promise?.reject("ERROR", "Image picker canceled")
                }
            }

            IMAGE_CAPTURE_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    currentPhotoPath?.let { path ->
                        val photoFile = File(path)
                        val photoUri = Uri.fromFile(photoFile)
                        startCropping(photoUri)
                    } ?: promise?.reject("ERROR", "Failed to capture image")
                } else {
                    promise?.reject("ERROR", "Image capture canceled")
                }
            }

            UCrop.REQUEST_CROP -> {
                if (resultCode == Activity.RESULT_OK) {
                    val resultUri = UCrop.getOutput(data!!)
                    resultUri?.let { uri ->
                        val croppedUri = if (options?.getString("cropType") == "circular") {
                            applyCircularMask(uri)
                        } else {
                            uri // Return original URI if cropType is rectangular
                        }
                        promise?.resolve(croppedUri.toString())
                    } ?: promise?.reject("ERROR", "Failed to crop image")
                } else if (resultCode == UCrop.RESULT_ERROR) {
                    val cropError = UCrop.getError(data!!)
                    promise?.reject(
                        "ERROR",
                        cropError?.message ?: "Unknown error during image cropping"
                    )
                } else {
                    promise?.reject("ERROR", "User canceled image cropping")
                }
            }

            else -> {
                promise?.reject("ERROR", "Unhandled activity result")
            }
        }
    }

    private fun startCropping(sourceUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val destinationUri = Uri.fromFile(File(reactApplicationContext.cacheDir, "cropped"))
                // Potentially add steps here to optimize the image before cropping
                withContext(Dispatchers.Main) {
                    // Ensure UCrop activity is started on the main thread
                    UCrop.of(sourceUri, destinationUri)
                        .withAspectRatio(1f, 1f)
                        .withOptions(getUCropOptions())
                        .start(currentActivity!!)
                }
            } catch (e: Exception) {
                // Handle any exceptions, potentially logging or notifying the user
                promise?.reject("ERROR", "Exception during image cropping: ${e.message}")
            }
        }
    }

    private fun getUCropOptions(): UCrop.Options {
        val options = UCrop.Options()
        // Check for cropType key
        if (this.options?.hasKey("cropType") == true) {
            options.setCircleDimmedLayer(this.options?.getString("cropType") == "circular")
        } else {
            options.setCircleDimmedLayer(false) // Default value
        }

        // Check for freeStyleCropEnabled key
        if (this.options?.hasKey("freeStyleCropEnabled") == true) {
            options.setFreeStyleCropEnabled(
                this.options?.getBoolean("freeStyleCropEnabled") ?: false
            )
        } else {
            options.setFreeStyleCropEnabled(true) // Default value
        }

        // Check for showCropFrame key
        if (this.options?.hasKey("showCropFrame") == true) {
            options.setShowCropFrame(this.options?.getBoolean("showCropFrame") ?: false)
        } else {
            options.setShowCropFrame(true) // Default value
        }

        // Check for showCropGrid key
        if (this.options?.hasKey("showCropGrid") == true) {
            options.setShowCropGrid(this.options?.getBoolean("showCropGrid") ?: false)
        } else {
            options.setShowCropGrid(true) // Default value
        }

        // Check for dimmedLayerColor key
        val dimmedLayerColor = if (this.options?.hasKey("dimmedLayerColor") == true) {
            val colorString = this.options?.getString("dimmedLayerColor")
            Color.parseColor(colorString)
        } else {
            Color.parseColor("#99000000") // Default color
        }
        options.setDimmedLayerColor(dimmedLayerColor)
        return options
    }

    private fun applyCircularMask(uri: Uri): Uri {
        if (options?.getString("cropType") == "circular") {
            val bitmap = BitmapFactory.decodeStream(
                reactApplicationContext.contentResolver.openInputStream(uri)
            )
            val circularBitmap = getCircularBitmap(bitmap)
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${NAME}_${timeStamp}.png"
            val file = File(reactApplicationContext.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            val imageQuality = try {
                val imageQualityString = if (this.options?.hasKey("imageQuality") == true) {
                    this.options!!.getString("imageQuality")
                } else {
                    null
                }
                imageQualityString?.toInt()?.coerceIn(60, 100) ?: 60
            } catch (e: NumberFormatException) {
                60 // Default value in case of format exception
            }
            circularBitmap.compress(Bitmap.CompressFormat.PNG, imageQuality, outputStream)
            outputStream.close()
            return Uri.fromFile(file)
        } else {
            return uri // Return original URI if cropType is rectangular
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val minEdge = Math.min(width, height)
        val dx = (width - minEdge) / 2
        val dy = (height - minEdge) / 2

        val dstBitmap = Bitmap.createBitmap(bitmap, dx, dy, minEdge, minEdge)

        val output = Bitmap.createBitmap(dstBitmap.width, dstBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint()
        val rect = Rect(0, 0, dstBitmap.width, dstBitmap.height)

        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.isDither = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(dstBitmap.width / 2f, dstBitmap.height / 2f, dstBitmap.width / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(dstBitmap, rect, rect, paint)

        dstBitmap.recycle()

        return output
    }

    companion object {
        const val IMAGE_PICKER_REQUEST = 1
        const val IMAGE_CAPTURE_REQUEST = 2
        const val NAME = "CropImage"
    }
}
