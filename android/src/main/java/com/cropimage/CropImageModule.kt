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
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.facebook.react.bridge.Arguments
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
import java.util.concurrent.atomic.AtomicInteger

class CropImageModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var promise: Promise? = null
  private var options: ReadableMap? = null
  private var currentPhotoPath: String? = null

  private val IMAGE_PICKER_REQUEST = 1
  private val IMAGE_CAPTURE_REQUEST = 2
  private val PERMISSIONS_REQUEST_CAMERA = 101

  private var pendingUris = mutableListOf<Uri>()
  private var processedUris = mutableListOf<String>()
  private var processingCount = AtomicInteger(0)


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
    pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, options?.getBoolean("multipleImage") == true)
    pickIntent.action = Intent.ACTION_GET_CONTENT

    try {
      currentActivity?.startActivityForResult(pickIntent, IMAGE_PICKER_REQUEST)
    } catch (e: Exception) {
      promise.reject("ERROR", "Failed to launch image picker: ${e.message}")
    }
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
    val defaultOptions = getDefaultOptions()
    val mergedOptions = Arguments.createMap()

    // Merge with default values
    mergedOptions.putBoolean(
      "cropEnabled",
      if (options.hasKey("cropEnabled")) options.getBoolean("cropEnabled") else defaultOptions.getBoolean(
        "cropEnabled"
      )
    )
    mergedOptions.putString(
      "cropType",
      if (options.hasKey("cropType")) options.getString("cropType") else defaultOptions.getString("cropType")
    )
    mergedOptions.putBoolean(
      "freeStyleCropEnabled",
      if (options.hasKey("freeStyleCropEnabled")) options.getBoolean("freeStyleCropEnabled") else defaultOptions.getBoolean(
        "freeStyleCropEnabled"
      )
    )
    mergedOptions.putBoolean(
      "showCropFrame",
      if (options.hasKey("showCropFrame")) options.getBoolean("showCropFrame") else defaultOptions.getBoolean(
        "showCropFrame"
      )
    )
    mergedOptions.putBoolean(
      "showCropGrid",
      if (options.hasKey("showCropGrid")) options.getBoolean("showCropGrid") else defaultOptions.getBoolean(
        "showCropGrid"
      )
    )
    mergedOptions.putString(
      "dimmedLayerColor",
      if (options.hasKey("dimmedLayerColor")) options.getString("dimmedLayerColor") else defaultOptions.getString(
        "dimmedLayerColor"
      )
    )

    mergedOptions.putInt(
      "imageQuality",
      if (options.hasKey("imageQuality")) {
        try {
          options.getDouble("imageQuality").toInt() // Assumes `imageQuality` is a number
        } catch (e: NumberFormatException) {
          defaultOptions.getInt("imageQuality") // Fallback if format is invalid
        } catch (e: Exception) {
          defaultOptions.getInt("imageQuality") // General fallback for any other exception
        }
      } else {
        defaultOptions.getInt("imageQuality") // Fallback if key does not exist
      }
    )
    mergedOptions.putBoolean(
      "multipleImage",
      if (options.hasKey("multipleImage")) options.getBoolean("multipleImage") else defaultOptions.getBoolean(
        "multipleImage"
      )
    )
    mergedOptions.putInt(
      "maxImages",
      if (options.hasKey("maxImages")) {
        try {
          options.getDouble("maxImages").toInt() // Assumes `imageQuality` is a number
        } catch (e: NumberFormatException) {
          defaultOptions.getInt("maxImages") // Fallback if format is invalid
        } catch (e: Exception) {
          defaultOptions.getInt("maxImages") // General fallback for any other exception
        }
      } else {
        defaultOptions.getInt("maxImages") // Fallback if key does not exist
      }
    )

    mergedOptions.putInt(
      "maxWidth",
      if (options.hasKey("maxWidth")) {
        try {
          options.getDouble("maxWidth").toInt()
        } catch (e: Exception) {
          defaultOptions.getInt("maxWidth")
        }
      } else {
        defaultOptions.getInt("maxWidth")
      }
    )

    mergedOptions.putInt(
      "maxHeight",
      if (options.hasKey("maxHeight")) {
        try {
          options.getDouble("maxHeight").toInt()
        } catch (e: Exception) {
          defaultOptions.getInt("maxHeight")
        }
      } else {
        defaultOptions.getInt("maxHeight")
      }
    )

    mergedOptions.putDouble(
      "maxFileSize",
      if (options.hasKey("maxFileSize")) {
        try {
          options.getDouble("maxFileSize")
        } catch (e: Exception) {
          defaultOptions.getDouble("maxFileSize")
        }
      } else {
        defaultOptions.getDouble("maxFileSize")
      }
    )

    this.options = mergedOptions
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
          if (options?.getBoolean("multipleImage") == true) {
            handleMultipleImage(data)
          } else {
            handleSingleImage(data?.data)
          }
        } else {
          sendErrorResponse("Image picker canceled")
        }
      }

      IMAGE_CAPTURE_REQUEST -> {
        if (resultCode == Activity.RESULT_OK) {
          currentPhotoPath?.let { path ->
            val photoFile = File(path)
            val photoUri = Uri.fromFile(photoFile)
            handleSingleImage(photoUri)
          } ?: sendErrorResponse("Failed to capture image")
        } else {
          sendErrorResponse("Image capture canceled")
        }
      }

      UCrop.REQUEST_CROP -> {
        if (resultCode == Activity.RESULT_OK) {
          val resultUri = UCrop.getOutput(data!!)
          handleSingleImage(resultUri)
        } else if (resultCode == UCrop.RESULT_ERROR) {
          val cropError = UCrop.getError(data!!)
          sendErrorResponse(cropError?.message ?: "Unknown error during image cropping")
        } else {
          sendErrorResponse("User canceled image cropping")
        }
      }
    }
  }

  private fun handleSingleImage(uri: Uri?) {
    if (uri == null) {
      sendErrorResponse("No image selected")
      return
    }

    CoroutineScope(Dispatchers.Main).launch {
      try {
        val compressedUri = compressImage(uri)
        val result = Arguments.createMap().apply {
          // Create standard response object
          putMap("response", createResponseObject(listOf(compressedUri)))
          // Add metadata
          putBoolean("multiple", false)
          putInt("count", 1)
          putBoolean("hasErrors", false)
        }
        promise?.resolve(result)
      } catch (e: Exception) {
        sendErrorResponse("Failed to process image: ${e.message}")
      }
    }
  }

  private fun handleMultipleImage(data: Intent?) {
    val maxImages = options?.getInt("maxImages") ?: DEFAULT_MAX_IMAGES
    val selectedUris = mutableListOf<Uri>()

    when {
      data?.clipData != null -> {
        val clipData = data.clipData!!
        if (clipData.itemCount > maxImages) {
          sendErrorResponse("You can select up to $maxImages images only")
          return
        }
        for (i in 0 until clipData.itemCount) {
          selectedUris.add(clipData.getItemAt(i).uri)
        }
      }

      data?.data != null -> {
        selectedUris.add(data.data!!)
      }

      else -> {
        sendErrorResponse("No images selected")
        return
      }
    }

    if (selectedUris.isEmpty()) {
      sendErrorResponse("No images selected")
      return
    }

    CoroutineScope(Dispatchers.Main).launch {
      try {
        val processedUris = selectedUris.mapNotNull { uri ->
          try {
            compressImage(uri)
          } catch (e: Exception) {
            Log.e(NAME, "Error processing image: ${e.message}")
            null
          }
        }

        val result = Arguments.createMap().apply {
          // Create standard response object
          putMap("response", createResponseObject(processedUris))
          // Add metadata
          putBoolean("multiple", true)
          putInt("count", processedUris.size)
          putBoolean("hasErrors", processedUris.size < selectedUris.size)
        }
        promise?.resolve(result)
      } catch (e: Exception) {
        sendErrorResponse("Failed to process images: ${e.message}")
      }
    }
  }

  private fun createResponseObject(uris: List<Uri>): ReadableMap {
    val response = Arguments.createMap()
    val images = Arguments.createArray()

    uris.forEachIndexed { index, uri ->
      val imageInfo = Arguments.createMap().apply {
        putString("uri", uri.toString())
        val size = getImageSize(uri)
        putInt("width", size.first)
        putInt("height", size.second)
        putDouble("size", getFileSizeInMB(uri))
        putInt("index", index)
        putString("type", getMimeType(uri))
        putString("fileName", getFileName(uri))
        putDouble("timestamp", System.currentTimeMillis().toDouble())
      }
      images.pushMap(imageInfo)
    }

    response.putArray("images", images)
    return response
  }

  private fun sendErrorResponse(message: String) {
    val errorResponse = Arguments.createMap().apply {
      putMap("response", Arguments.createMap().apply {
        putArray("images", Arguments.createArray())
      })
      putBoolean("multiple", options?.getBoolean("multipleImage") == true)
      putInt("count", 0)
      putBoolean("hasErrors", true)
      putString("errorMessage", message)
    }
    promise?.resolve(errorResponse)
  }

  private fun compressImage(uri: Uri): Uri {
    try {
      var bitmap = BitmapFactory.decodeStream(
        reactApplicationContext.contentResolver.openInputStream(uri)
      ) ?: throw IOException("Failed to decode image")

      // Get max dimensions from options
      val maxWidth = options?.getInt("maxWidth") ?: DEFAULT_MAX_WIDTH
      val maxHeight = options?.getInt("maxHeight") ?: DEFAULT_MAX_HEIGHT
      val maxFileSize = options?.getDouble("maxFileSize") ?: DEFAULT_MAX_FILE_SIZE

      // Scale if needed
      if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
        val scaleWidth = maxWidth.toFloat() / bitmap.width
        val scaleHeight = maxHeight.toFloat() / bitmap.height
        val scaleFactor = minOf(scaleWidth, scaleHeight)

        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        bitmap.recycle()
        bitmap = scaledBitmap
      }

      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
      val fileName = "${NAME}_${timeStamp}_${System.nanoTime()}.jpg"
      val file = File(reactApplicationContext.cacheDir, fileName)

      var quality = options?.getInt("imageQuality") ?: DEFAULT_IMAGE_QUALITY
      var fileSize: Double
      var attempts = 0
      val maxAttempts = 5

      do {
        FileOutputStream(file).use { outputStream ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
          outputStream.flush()
        }

        fileSize = file.length().toDouble() / (1024 * 1024) // Convert to MB
        quality = (quality * 0.8).toInt().coerceAtLeast(10)
        attempts++
      } while (fileSize > maxFileSize && attempts < maxAttempts && quality > 10)

      bitmap.recycle()
      return Uri.fromFile(file)
    } catch (e: Exception) {
      throw IOException("Failed to compress image: ${e.message}")
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
          this.options!!.getInt("imageQuality")
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

    private val DEFAULT_CROP_ENABLED = true
    private val DEFAULT_CROP_TYPE = "rectangular"
    private val DEFAULT_FREE_STYLE_CROP_ENABLED = false
    private val DEFAULT_SHOW_CROP_FRAME = false
    private val DEFAULT_SHOW_CROP_GRID = false
    private val DEFAULT_DIMMED_LAYER_COLOR = "#99000000"
    private val DEFAULT_IMAGE_QUALITY = 60
    private val DEFAULT_Multiple_IMAGE = false
    private val DEFAULT_MAX_IMAGES = 50
    private val DEFAULT_MAX_WIDTH = 1920
    private val DEFAULT_MAX_HEIGHT = 1280
    private val DEFAULT_MAX_FILE_SIZE = 10.0 // in MB

    const val NAME = "CropImage"
  }


  // Add getDefaultOptions function
  private fun getDefaultOptions(): ReadableMap {
    val defaultMap = Arguments.createMap().apply {
      putBoolean("cropEnabled", DEFAULT_CROP_ENABLED)
      putString("cropType", DEFAULT_CROP_TYPE)
      putBoolean("freeStyleCropEnabled", DEFAULT_FREE_STYLE_CROP_ENABLED)
      putBoolean("showCropFrame", DEFAULT_SHOW_CROP_FRAME)
      putBoolean("showCropGrid", DEFAULT_SHOW_CROP_GRID)
      putString("dimmedLayerColor", DEFAULT_DIMMED_LAYER_COLOR)
      putInt("imageQuality", DEFAULT_IMAGE_QUALITY)
      putBoolean("multipleImage", DEFAULT_Multiple_IMAGE)
      putInt("maxImages", DEFAULT_MAX_IMAGES)
      putInt("maxWidth", DEFAULT_MAX_WIDTH)
      putInt("maxHeight", DEFAULT_MAX_HEIGHT)
      putDouble("maxFileSize", DEFAULT_MAX_FILE_SIZE)
    }
    return defaultMap
  }

  // Add new helper function to get image dimensions
  private fun getImageSize(uri: Uri): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(
      reactApplicationContext.contentResolver.openInputStream(uri),
      null,
      options
    )
    return Pair(options.outWidth, options.outHeight)
  }

  // Add new helper function to get file size in MB
  private fun getFileSizeInMB(uri: Uri): Double {
    return try {
      val file = File(uri.path!!)
      if (file.exists()) {
        file.length().toDouble() / (1024 * 1024) // Convert bytes to MB
      } else {
        val inputStream = reactApplicationContext.contentResolver.openInputStream(uri)
        val bytes = inputStream?.available()?.toLong() ?: 0L
        inputStream?.close()
        bytes.toDouble() / (1024 * 1024) // Convert bytes to MB
      }
    } catch (e: Exception) {
      Log.e(NAME, "Error getting file size: ${e.message}")
      0.0
    }
  }

  // Helper function to get MIME type
  private fun getMimeType(uri: Uri): String {
    return reactApplicationContext.contentResolver.getType(uri) ?: "image/jpeg"
  }

  // Helper function to get file name
  private fun getFileName(uri: Uri): String {
    val cursor = reactApplicationContext.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
      val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      it.moveToFirst()
      it.getString(nameIndex)
    } ?: run {
      uri.lastPathSegment ?: "unknown"
    }
  }
}
