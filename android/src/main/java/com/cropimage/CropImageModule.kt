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
            data?.data?.let { uri ->
              if (options?.getBoolean("cropEnabled") == true) {
                startCropping(uri)
              } else {
                val compressedUri = compressImage(uri)
                val result = Arguments.createMap().apply {
                  putString("uri", compressedUri.toString())
                  val size = getImageSize(compressedUri)
                  putInt("width", size.first)
                  putInt("height", size.second)
                  putDouble("size", getFileSizeInMB(compressedUri))
                }
                promise?.resolve(result)
              }
            } ?: promise?.reject("ERROR", "Failed to pick image")
          }
        } else {
          promise?.reject("ERROR", "Image picker canceled")
        }
      }

      IMAGE_CAPTURE_REQUEST -> {
        if (resultCode == Activity.RESULT_OK) {
          currentPhotoPath?.let { path ->
            val photoFile = File(path)
            val photoUri = Uri.fromFile(photoFile)
            // Check if cropping is enabled
            if (options?.getBoolean("cropEnabled") == true) {
              startCropping(photoUri)
            } else {
              // Return the original uri if cropping is disabled
              promise?.resolve(photoUri.toString())
            }
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

  private fun handleMultipleImage(data: Intent?) {
    pendingUris.clear()
    processedUris.clear()
    processingCount.set(0)

    val maxImages = options?.getInt("maxImages") ?: DEFAULT_MAX_IMAGES

    // Create a list to store URIs with their original indices
    val indexedUris = mutableListOf<Pair<Int, Uri>>()

    when {
      data?.clipData != null -> {
        val clipData = data.clipData!!
        if (clipData.itemCount > maxImages) {
          promise?.reject("ERROR", "You can select up to $maxImages images only.")
          return
        }
        // Store URIs in reverse order to maintain original selection order
        for (i in (clipData.itemCount - 1) downTo 0) {
          indexedUris.add(Pair(i, clipData.getItemAt(i).uri))
        }
      }
      data?.data != null -> {
        indexedUris.add(Pair(0, data.data!!))
      }
      else -> {
        promise?.reject("ERROR", "No images selected")
        return
      }
    }

    if (indexedUris.isEmpty()) {
      promise?.reject("ERROR", "No images selected")
      return
    }

    CoroutineScope(Dispatchers.Main).launch {
      try {
        val resultArray = Arguments.createArray()
        val processedResults = mutableListOf<Pair<Int, ReadableMap>>()
        
        // Process images and maintain their original indices
        withContext(Dispatchers.IO) {
          indexedUris.forEach { (originalIndex, uri) ->
            val compressedUri = compressImage(uri)
            val imageInfo = Arguments.createMap().apply {
              putString("uri", compressedUri.toString())
              val size = getImageSize(compressedUri)
              putInt("width", size.first)
              putInt("height", size.second)
              putDouble("size", getFileSizeInMB(compressedUri))
              putInt("index", originalIndex)
            }
            processedResults.add(Pair(originalIndex, imageInfo))
          }
        }

        // Sort results in descending order to match original selection
        processedResults.sortedByDescending { it.first }.forEach { (_, imageInfo) ->
          resultArray.pushMap(imageInfo)
        }
        
        promise?.resolve(resultArray)
      } catch (e: Exception) {
        promise?.reject("ERROR", "Failed to process images: ${e.message}")
      }
    }
  }

  private fun compressImage(uri: Uri): Uri {
    val bitmap = BitmapFactory.decodeStream(
      reactApplicationContext.contentResolver.openInputStream(uri)
    )

    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
    val randomId = System.nanoTime() // Add a unique identifier
    val fileName = "${NAME}_${timeStamp}_${randomId}.jpg"
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

    bitmap.compress(Bitmap.CompressFormat.JPEG, imageQuality, outputStream)
    outputStream.close()
    bitmap.recycle()

    return Uri.fromFile(file)
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
    try {
      val file = File(uri.path!!)
      val bytes = file.length()
      return String.format("%.2f", bytes.toDouble() / (1024 * 1024)).toDouble()
    } catch (e: Exception) {
      Log.e(NAME, "Error getting file size: ${e.message}")
      return 0.0
    }
  }
}
