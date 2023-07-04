package org.pytorch.demo.objectdetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

// https://github.com/pytorch/android-demo-app/tree/master/ObjectDetection
public class MainActivity extends AppCompatActivity implements Runnable {
    private int mImageIndex = 0;
    private String[] mTestImages;
    final private String imagesPath = "examples/";
    private Button buttonTest;

    private ImageView mImageView;
    private ResultView mResultView;
    private Button mButtonDetect;
    private TextView textViewDetection;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;
    private Module mModule = null;  // our model
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;

    CameraController cameraController;
    private PreviewView previewView;
    final int PERMISSION_REQUEST_CODE = 1;

    // Function to get path of assets as YOLO weights
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        // Check permission for loading images from gallery
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        // Check permission to use camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }
        // Set layout
        setContentView(R.layout.activity_main);
        textViewDetection = findViewById(R.id.textViewDetect);

        // Show image
        mImageView = findViewById(R.id.imageView);
        loadDefaultImage();
        // Hide resultView
        mResultView = findViewById(R.id.resultView);
        mResultView.setVisibility(View.INVISIBLE);
        mResultView.getLayoutParams().height = mImageView.getHeight();
        // Hide previewView
        previewView = findViewById(R.id.prevView);
        previewView.getLayoutParams().height = mImageView.getHeight();
        previewView.setVisibility(View.INVISIBLE);

        // Test default images
        try {
            AssetManager assetManager = getAssets();
            mTestImages = assetManager.list("examples");
        } catch (IOException e) {Log.e("", "Problems with examples images.");}
        buttonTest = findViewById(R.id.testButton);
        buttonTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mTestImages == null) return;
                mResultView.setVisibility(View.INVISIBLE);
                mImageIndex = (mImageIndex + 1) % mTestImages.length;
                String imageName = mTestImages[mImageIndex];
                Log.i("EXEMPLE LOADED", imageName);
                try {
                    textViewDetection.setText("");
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(imagesPath + imageName));
                    mImageView.setImageBitmap(mBitmap);
                } catch (IOException e) {
                    Log.e("Object Detection", "Error reading assets", e);
                    finish();
                }
            }
        });

        // Load image from gallery
        final Button buttonSelect = findViewById(R.id.selectButton);
        buttonSelect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                // Building popup
                final CharSequence[] options = { "Open Gallery", "Back" };
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("New Test Image");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (item == 0) { // open gallery
                            Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(pickPhoto , 1);
                        }
                        else if (item == 1) {  // back
                            dialog.dismiss();
                        }
                    }
                });
                builder.show();
            }
        });

        // Detect image loaded in the mImageView
        mButtonDetect = findViewById(R.id.detectButton);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonDetect.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonDetect.setText(getString(R.string.run_model));
                //mBitmap =((BitmapDrawable)imageView.getDrawable()).getBitmap();
                // Scale image to 320x320
                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;
                // Run on a new Thread
                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        // Try to load our trained YOLOv5 model
        try {
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "yolo_dogs_breeds.torchscript.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("dogs_breeds.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }

        LifecycleOwner lifecycleOwner = this;
        cameraController = new CameraController(this);
        // Open camera and detect
        final Button buttonLive = findViewById(R.id.liveButton);
        buttonLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!cameraController.is_camera_open) {
                    cameraController.startCamera(previewView, mImageView, mResultView, lifecycleOwner);
                    buttonLive.setText("Close Camera");
                }
                else {
                    cameraController.closeCamera();
                    loadDefaultImage();
                    buttonLive.setText(getString(R.string.live));
                    textViewDetection.setText("");
                }
            }
        });
    }

    private void loadDefaultImage() {
        // Load default image
        try {
            String defaultImageName = "test.jpg";
            mBitmap = BitmapFactory.decodeStream(getAssets().open(defaultImageName));
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
        mImageView.setImageBitmap(mBitmap);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                }
                else {
                    Toast.makeText(getApplicationContext(), "Permission to Camera Denied", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0: // photo taken
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");
                        Matrix matrix = new Matrix();
                        //matrix.postRotate(90.0f);
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                    }
                    break;
                case 1: // photo loaded from gallery
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                cursor.close();
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                //Matrix matrix = new Matrix();
                                //matrix.postRotate(90.0f);
                                //mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    // Run one detection of the image loaded in the imageView when pressing mButtonDetect
    @Override
    public void run() {
        // Get scaled bitmap
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);
        // Create tensor
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        // Forward tensor for inference
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        // Retrieve output tensor
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        // Get class result
        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);
        // Update UI
        runOnUiThread(() -> {
            //mImageView.setImageBitmap(resizedBitmap);
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mResultView.setResults(results);
            mResultView.invalidate();
            mResultView.setVisibility(View.VISIBLE);
        });
    }
}
