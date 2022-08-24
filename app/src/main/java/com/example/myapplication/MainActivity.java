package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    static int loaded;
    static {
        if(!OpenCVLoader.initDebug()) {
            Log.d("TAG", "not loaded");
            loaded = 0xff;
        }
        else {
            Log.d("TAG", "done");
            loaded = 0;
        }
    }

    private final String TAG = MainActivity.class.getSimpleName();
    private CameraBridgeViewBase mOpenCvCameraView;
    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(TAG, "OpenCV loaded successfully");
                mOpenCvCameraView.enableView();
            }
            else {
                super.onManagerConnected(status);
            }
        }
    };

    private ActivityMainBinding binding;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityMainBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        checkPermission();
        mOpenCvCameraView = findViewById(R.id.camView);

        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mLoaderCallback.onManagerConnected(loaded);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(!OpenCVLoader.initDebug())
            Log.d(TAG, "not loaded");
//        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        else {
            Log.d(TAG, "resume-done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {}

    @Override
    public void onCameraViewStopped() {}

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    is_granted -> {
                        if (!is_granted) {
                            Snackbar.make(
                                    binding.getRoot(),
                                    "Grant camera permission",
                                    BaseTransientBottomBar.LENGTH_INDEFINITE
                            ).show();
                        }
                    }
            );
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        }
    }
}