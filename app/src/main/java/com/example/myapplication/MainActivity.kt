package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.utils.EffectUtils
import com.example.myapplication.utils.ImageUtils
import com.google.mediapipe.solutioncore.ResultListener
import com.google.mediapipe.solutions.facemesh.FaceMesh
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {
    companion object {
        private val ORIENTATIONS = SparseIntArray()
        const val REQUEST_CAMERA_PERMISSION = 200

        init {
            OpenCVLoader.initDebug()
        }

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private val _logTag = MainActivity::class.java.simpleName
    private lateinit var binding: ActivityMainBinding

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSessions: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private lateinit var imageDimension: Size
    private var imageReader: ImageReader? = null
    private var imageView: ImageView? = null
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var frameTime: Long = Long.MIN_VALUE

    private var height = 0
    private var width = 0


    private val imageAvailableListener =
        ImageReader.OnImageAvailableListener { ir -> // Contrary to what is written in Aptina presentation acquireLatestImage is not working as described
            // Google: Also, not working as described in android docs (should work the same as acquireNextImage in
            // our case, but it is not)
            // Image im = ir.acquireLatestImage();

//            Log.d(_logTag, "timestamp: ${ System.currentTimeMillis() }")
            if (frameTime == Long.MIN_VALUE) {
                frameTime = System.currentTimeMillis()
                height = ir.height
                width = ir.width
                Log.d("$_logTag ->", "$height, $width")
            }
            // uncomment this to get fps based on frame time
//            else {
//                val currentTime = System.currentTimeMillis()
//                Log.d(_logTag, "${ 1000.0/(currentTime - frameTime)}")
//                frameTime = currentTime
//            }


            val image = ir.acquireNextImage()
            val mYuvMat = ImageUtils.imageToMat(image)
            val matOutRgb = Mat()

            Imgproc.cvtColor(mYuvMat, matOutRgb, Imgproc.COLOR_YUV2RGBA_I420)
            Core.flip(matOutRgb, matOutRgb, 0)
            Core.rotate(matOutRgb, matOutRgb, Core.ROTATE_90_COUNTERCLOCKWISE)

            val bitmap = Bitmap.createBitmap(
                matOutRgb.cols(),
                matOutRgb.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(matOutRgb, bitmap)
            val timestamp = System.currentTimeMillis()
            faceMesh.send(bitmap, timestamp)
            images[timestamp] = matOutRgb
            image.close()
        }

    var stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, i: Int) {
            cameraDevice.close()
        }
    }

    private lateinit var faceMesh: FaceMesh
    private val images = HashMap<Long, Mat>()
    private var boolean: Boolean = true

    private var faceMeshResultListener: ResultListener<FaceMeshResult> = ResultListener {
        Log.d(_logTag, "Face count: ${ it.multiFaceLandmarks().size }")
        var image = images[it.timestamp()]
        if (it.multiFaceLandmarks().size > 0) {
            val cords = ArrayList<Point>(1564)
            val landmarksList = it.multiFaceLandmarks()[0].landmarkList

            for (i in listOf(70, 117, 300, 346)) {
                cords.add(Point(((landmarksList[i].x * 720).toDouble()), ((landmarksList[i].y * 1280).toDouble())))
//                Imgproc.drawMarker(image, cords.last(), Scalar(255.0, 255.0, 255.0, 255.0), Imgproc.MARKER_TILTED_CROSS, 15, 2)
//                Log.d(_logTag, "${ cords.last().x }, ${ cords.last().y }")
            }
            Log.d(_logTag, "angle: ${ image!![0, 0].size }")
            image = EffectUtils.addEffect(this@MainActivity, image!!, cords)
        }
        val bitmap = Bitmap.createBitmap(
            image!!.cols(),
            image.rows(),
            Bitmap.Config.ARGB_8888
        )
        Log.d(_logTag, "${ image.cols() }, ${ image.rows() }")
        Utils.matToBitmap(image, bitmap)

        runOnUiThread {
            Log.d(_logTag, "setting image")
            imageView!!.setImageBitmap(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)
        imageView = binding.imageView
        setupFaceMesh()
    }

    private fun setupFaceMesh() {
        Log.i(_logTag, "Starting setup...")
        faceMesh = FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(true)
                .build()
        )
        Log.i(_logTag, "FaceMesh initialized...")
        faceMesh.setErrorListener { message, _ -> Log.e(_logTag, "Error MediaPipe Face Mesh -> $message") }
        Log.i(_logTag, "FaceMesh initialized... -> Done")
        faceMesh.setResultListener(faceMeshResultListener)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun createCameraPreview() {
        try {
            val surfaces: MutableList<Surface> = ArrayList()
            val surface = imageReader!!.surface
            surfaces.add(surface)

            //create capture request
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder!!.addTarget(surface)

            //create capture session
            cameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        cameraCaptureSessions = cameraCaptureSession
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Capture session failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions!!.setRepeatingRequest(
                captureRequestBuilder!!.build(),
                object : CaptureCallback() {
                    override fun onCaptureStarted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        timestamp: Long,
                        frameNumber: Long
                    ) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber)
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[1]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[1]

            //Check realtime permission if run higher API 23
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), MainActivity.REQUEST_CAMERA_PERMISSION
                )
                return
            }

            imageReader = ImageReader.newInstance(
                imageDimension.width/2,
                imageDimension.height/2,
                ImageFormat.YUV_420_888,
                10
            )
            imageReader!!.setOnImageAvailableListener(
                imageAvailableListener,
                null
            )
            manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        openCamera()
    }

    override fun onPause() {
        stopBackgroundThread()
        super.onPause()
    }

    private fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

}