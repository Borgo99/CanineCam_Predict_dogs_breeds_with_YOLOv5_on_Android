package org.pytorch.demo.objectdetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class CameraController extends View implements ImageAnalysis.Analyzer {


    private PreviewView previewView;
    private ImageView imageView;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAnalysis;
    private final int cameraSelected = CameraSelector.LENS_FACING_BACK;
    //private final int cameraSelected = CameraSelector.LENS_FACING_FRONT;
    private ListenableFuture<ProcessCameraProvider> provider;
    private ProcessCameraProvider cameraProvider;

    public boolean is_camera_open;
    private Module mModule = null;
    private ResultView mResultView;

    public CameraController(Context context) {
        super(context);
        is_camera_open = false;

        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(context, "yolo_dogs_breeds.torchscript.ptl"));
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
        }
    }

    public void startCamera(PreviewView previewView, ImageView imageView, ResultView resultView, LifecycleOwner lifecycleOwner) {
        this.previewView = previewView;
        this.imageView = imageView;
        this.mResultView = resultView;
        this.is_camera_open = true;

        imageView.setImageDrawable(null);
        previewView.setVisibility(View.VISIBLE);

        provider = ProcessCameraProvider.getInstance(getContext());
        provider.addListener( () ->
        {
            try {
                ProcessCameraProvider cameraProvider = provider.get();
                if (cameraProvider == null) {
                    return;
                }

                cameraProvider.unbindAll();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(
                        cameraSelected ).build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                imageAnalysis.setAnalyzer(getExecutor(), this);

                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapt, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    public void closeCamera() {
        this.is_camera_open = false;
        this.previewView.setVisibility(View.INVISIBLE);
        mResultView.invalidate();
        mResultView.setVisibility(View.INVISIBLE);

        provider = ProcessCameraProvider.getInstance(getContext());
        provider.addListener( () ->
        {
            try {
                ProcessCameraProvider cameraProvider = provider.get();
                if (cameraProvider == null) {
                    return;
                }

                cameraProvider.unbindAll();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private Executor getExecutor() {return ContextCompat.getMainExecutor(getContext()); }

    @Override
    @Nullable
    public void analyze(@NonNull ImageProxy image) {
        if (!this.is_camera_open) {return;}
        Bitmap bitmap = this.previewView.getBitmap();

        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float) bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float) bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float) mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float) mResultView.getHeight() / bitmap.getHeight();
        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        Log.i("Number of detections: ", String.valueOf(results.size()));

        mResultView.setResults(results);
        mResultView.invalidate();
        mResultView.setVisibility(View.VISIBLE);

        image.close();
    }
}
