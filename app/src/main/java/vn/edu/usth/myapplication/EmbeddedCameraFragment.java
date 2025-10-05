/*
 * Copyright (c) 2025 Android project OpenVision API
 * All rights reserved.
 * Project: My Application
 * File: EmbeddedCameraFragment.java
 * Last Modified: 5/10/2025 3:34
 */

package vn.edu.usth.myapplication;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbeddedCameraFragment extends Fragment {

    private static final String TAG = "EmbeddedCameraFragment";

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private View permissionLayout;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private Camera camera;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private SeekBar zoomSlider;
    private TextView txtZoomLevel;
    private View zoomControlLayout;
    private Button btnZoom1x, btnZoom2x;
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomRatio = 1.0f;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    startCamera();
                } else {
                    showPermissionLayout();
                    Toast.makeText(requireContext(), "Permissions denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        previewView = view.findViewById(R.id.preview_view);
        permissionLayout = view.findViewById(R.id.permission_layout);
        zoomSlider = view.findViewById(R.id.zoom_slider);
        txtZoomLevel = view.findViewById(R.id.txt_zoom_level);
        zoomControlLayout = view.findViewById(R.id.zoom_control);
        btnZoom1x = view.findViewById(R.id.btn_zoom_1x);
        btnZoom2x = view.findViewById(R.id.btn_zoom_2x);

        // Camera controls
        FloatingActionButton btnCapture = view.findViewById(R.id.btn_capture);
        FloatingActionButton btnSwitchCamera = view.findViewById(R.id.btn_switch_camera);
        FloatingActionButton btnGallery = view.findViewById(R.id.btn_gallery);
        MaterialButton btnGrantPermission = view.findViewById(R.id.btn_grant_permission);

        // Set up button click listeners
        btnCapture.setOnClickListener(v -> takePhoto());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        btnGrantPermission.setOnClickListener(v -> requestAppPermissions());

        // Set up zoom controls
        setupZoomControl();
        setupPinchToZoom();

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Check permissions and start camera
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            showPermissionLayout();
        }

        return view;
    }

    private void setupZoomControl() {
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (cameraControl != null && cameraInfo != null) {
                    float minZoom = cameraInfo.getZoomState().getValue().getMinZoomRatio();
                    float maxZoom = cameraInfo.getZoomState().getValue().getMaxZoomRatio();

                    // Calculate zoom ratio from progress (0-100)
                    currentZoomRatio = minZoom + (progress / 100f) * (maxZoom - minZoom);

                    // Apply zoom
                    cameraControl.setZoomRatio(currentZoomRatio);

                    // Update zoom level text
                    txtZoomLevel.setText(String.format(Locale.US, "%.1fx", currentZoomRatio));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Zoom preset buttons
        btnZoom1x.setOnClickListener(v -> setZoomRatio(1.0f));
        btnZoom2x.setOnClickListener(v -> setZoomRatio(2.0f));
    }

    private void setupPinchToZoom() {
        scaleGestureDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (cameraInfo != null && cameraControl != null) {
                    float scale = detector.getScaleFactor();
                    float minZoom = cameraInfo.getZoomState().getValue().getMinZoomRatio();
                    float maxZoom = cameraInfo.getZoomState().getValue().getMaxZoomRatio();

                    // Calculate new zoom ratio
                    currentZoomRatio *= scale;
                    currentZoomRatio = Math.max(minZoom, Math.min(currentZoomRatio, maxZoom));

                    // Apply zoom
                    cameraControl.setZoomRatio(currentZoomRatio);

                    // Update UI
                    updateZoomUI(currentZoomRatio, minZoom, maxZoom);
                }
                return true;
            }
        });

        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void setZoomRatio(float targetZoom) {
        if (cameraControl != null && cameraInfo != null) {
            float minZoom = cameraInfo.getZoomState().getValue().getMinZoomRatio();
            float maxZoom = cameraInfo.getZoomState().getValue().getMaxZoomRatio();

            // Clamp target zoom to valid range
            targetZoom = Math.max(minZoom, Math.min(targetZoom, maxZoom));
            currentZoomRatio = targetZoom;

            // Apply zoom
            cameraControl.setZoomRatio(targetZoom);

            // Update UI
            updateZoomUI(targetZoom, minZoom, maxZoom);
        }
    }

    private void updateZoomUI(float zoomRatio, float minZoom, float maxZoom) {
        // Update text
        txtZoomLevel.setText(String.format(Locale.US, "%.1fx", zoomRatio));

        // Update slider
        int progress = (int) ((zoomRatio - minZoom) / (maxZoom - minZoom) * 100);
        zoomSlider.setProgress(progress);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If user returned from Settings and granted permission, start camera
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            showPermissionLayout();
        }
    }

    private void requestAppPermissions() {
        List<String> req = new ArrayList<>();
        req.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Legacy external storage write is required to save to public Pictures on < 29
            req.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        requestPermissionsLauncher.launch(req.toArray(new String[0]));
    }

    private void startCamera() {
        permissionLayout.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
                Toast.makeText(requireContext(), "Failed to start camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
                showPermissionLayout();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Build ImageCapture with better error handling and compatibility
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture);

            // Get camera control for flash and zoom
            cameraControl = camera.getCameraControl();
            cameraInfo = camera.getCameraInfo();

            // Apply flash settings
            updateFlashMode();

            // Initialize zoom control
            initializeZoomControl();

        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(requireContext(), "Camera bind failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeZoomControl() {
        if (cameraInfo != null) {
            ZoomState zoomState = cameraInfo.getZoomState().getValue();
            if (zoomState != null) {
                float minZoom = zoomState.getMinZoomRatio();
                float maxZoom = zoomState.getMaxZoomRatio();

                // Reset zoom to minimum
                currentZoomRatio = minZoom;
                zoomSlider.setProgress(0);
                txtZoomLevel.setText(String.format(Locale.US, "%.1fx", minZoom));

                // Show zoom control if camera supports zoom
                if (maxZoom > minZoom) {
                    zoomControlLayout.setVisibility(View.VISIBLE);

                    // Enable/disable 2x button based on max zoom
                    btnZoom2x.setEnabled(maxZoom >= 2.0f);
                } else {
                    zoomControlLayout.setVisibility(View.GONE);
                }
            }
        }
    }

    private void updateFlashMode() {
        if (cameraControl != null && imageCapture != null) {
            boolean flashEnabled = SettingsFragment.isFlashEnabled(requireContext());
            if (flashEnabled) {
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else {
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera is not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update flash mode before capture
        updateFlashMode();

        String name = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis());

        ImageCapture.OutputFileOptions outputOptions;
        Uri savedContentUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use scoped storage via MediaStore
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CamStudy");

                outputOptions = new ImageCapture.OutputFileOptions
                        .Builder(requireContext().getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues)
                        .build();
            } else {
                // Legacy external storage: save to public Pictures/CamStudy
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CamStudy");
                if (!dir.exists() && !dir.mkdirs()) {
                    Toast.makeText(requireContext(), "Cannot access storage", Toast.LENGTH_SHORT).show();
                    return;
                }
                File photoFile = new File(dir, name + ".jpg");
                outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
                savedContentUri = Uri.fromFile(photoFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create output options", e);
            Toast.makeText(requireContext(), "Failed to prepare storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        Uri finalSavedContentUri = savedContentUri;
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        Toast.makeText(requireContext(), "Photo saved!", Toast.LENGTH_SHORT).show();
                        Uri uri = output.getSavedUri() != null ? output.getSavedUri() : finalSavedContentUri;
                        if (uri != null) {
                            long ts = System.currentTimeMillis();
                            try {
                                String[] proj = {MediaStore.Images.Media.DATE_TAKEN};
                                try (android.database.Cursor c = requireContext().getContentResolver()
                                        .query(uri, proj, null, null, null)) {
                                    if (c != null && c.moveToFirst()) {
                                        int idx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                                        long dateTaken = c.getLong(idx);
                                        if (dateTaken > 0) ts = dateTaken;
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to get date taken", e);
                            }
                            // Trigger media scan on legacy to make it appear in gallery apps
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                try {
                                    android.media.MediaScannerConnection.scanFile(requireContext(), new String[]{uri.getPath()}, new String[]{"image/jpeg"}, null);
                                } catch (Exception e) {
                                    Log.e(TAG, "Media scan failed", e);
                                }
                            }
                            try {
                                new PhotoDatabase(requireContext()).savePhoto(uri.toString(), ts);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to save to database", e);
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        String errorMsg = "Photo capture failed!";
                        if (exception.getMessage() != null && exception.getMessage().contains("CAMERA_CLOSED")) {
                            errorMsg = "Camera was closed. Please try again.";
                            startCamera(); // Restart camera
                        } else if (exception.getMessage() != null && exception.getMessage().contains("FILE_IO_ERROR")) {
                            errorMsg = "Storage error. Check storage permissions.";
                        }
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void switchCamera() {
        cameraSelector = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA ?
                CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        startCamera();
    }

    private void openGallery() {
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.navigate(R.id.nav_history);
    }

    private boolean allPermissionsGranted() {
        boolean cam = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!cam) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Need write for legacy public external storage
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void showPermissionLayout() {
        permissionLayout.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.GONE);
        zoomControlLayout.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
