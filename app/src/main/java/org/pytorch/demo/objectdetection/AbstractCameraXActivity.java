// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
//import androidx.camera.core.PreviewConfig;
import androidx.camera.core.impl.ImageAnalysisConfig;
import androidx.camera.core.impl.PreviewConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

public abstract class AbstractCameraXActivity<R> extends BaseModuleActivity implements ImageAnalysis.Analyzer  {
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};

    private long mLastAnalysisResultTime;

    private ListenableFuture<ProcessCameraProvider> provider;
    private final int cameraSelected = CameraSelector.LENS_FACING_FRONT;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAnalysis;
    private PreviewView previewView;

    protected abstract int getContentViewLayoutId();

    protected abstract TextureView getCameraPreviewTextureView();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutId());
        Log.wtf("WTF", "HERE");

        startBackgroundThread();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                PERMISSIONS,
                REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            provider = ProcessCameraProvider.getInstance(this);
            provider.addListener( () -> {
                try {
                    ProcessCameraProvider cameraProvider = provider.get();
                    startCamera(cameraProvider);
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }, getExecutor());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                    this,
                    "You can't use object detection example without granting CAMERA permission",
                    Toast.LENGTH_LONG)
                    .show();
                finish();
            } else {
                //startCamera();
            }
        }
    }

    private Executor getExecutor() {return ContextCompat.getMainExecutor(this); }

    private void startCamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(
                cameraSelected ).build();

        Preview preview = new Preview.Builder().build();
        //previewView = findViewById();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(getExecutor(), this);

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapt, imageAnalysis);
    }

    /* private void setupCameraX() {


        final TextureView textureView = getCameraPreviewTextureView();
        final PreviewConfig previewConfig = new PreviewConfig.Builder().build();
        final Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(output -> textureView.setSurfaceTexture(output.getSurfaceTexture()));

        final ImageAnalysisConfig imageAnalysisConfig =
            new ImageAnalysisConfig.Builder()
                .setTargetResolution(new Size(480, 640))
                .setCallbackHandler(mBackgroundHandler)
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .build();
        final ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer((image, rotationDegrees) -> {
            if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
                return;
            }

            final R result = analyzeImage(image, rotationDegrees);
            if (result != null) {
                mLastAnalysisResultTime = SystemClock.elapsedRealtime();
                runOnUiThread(() -> applyToUiAnalyzeImageResult(result));
            }
        });

        CameraX.bindToLifecycle(this, preview, imageAnalysis);
    }
    */

    @WorkerThread
    @Nullable
    protected abstract R analyzeImage(ImageProxy image, int rotationDegrees);

    @UiThread
    protected abstract void applyToUiAnalyzeImageResult(R result);
}
