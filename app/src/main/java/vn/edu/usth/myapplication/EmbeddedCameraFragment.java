/*
 * Copyright (c) 2025 Pham The Minh
 * All rights reserved.
 * Project: My Application
 * File: EmbeddedCameraFragment.java
 * Last Modified: 26/9/2025 8:33
 */

package vn.edu.usth.myapplication;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbeddedCameraFragment extends Fragment {

    private static final String TAG = "EmbeddedCameraFragment";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private View permissionLayout;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        previewView = view.findViewById(R.id.preview_view);
        permissionLayout = view.findViewById(R.id.permission_layout);

        // Camera controls
        FloatingActionButton btnCapture = view.findViewById(R.id.btn_capture);
        FloatingActionButton btnSwitchCamera = view.findViewById(R.id.btn_switch_camera);
        FloatingActionButton btnGallery = view.findViewById(R.id.btn_gallery);
        MaterialButton btnGrantPermission = view.findViewById(R.id.btn_grant_permission);

        // Set up button click listeners
        btnCapture.setOnClickListener(v -> takePhoto());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnGallery.setOnClickListener(v -> openGallery());
        btnGrantPermission.setOnClickListener(v -> requestPermissions());

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Check permissions and start camera
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            showPermissionLayout();
        }

        return view;
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
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        String name = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoMagic");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        String msg = "Photo capture succeeded: " + output.getSavedUri();
                        Toast.makeText(requireContext(), "Photo saved!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        Toast.makeText(requireContext(), "Photo capture failed!", Toast.LENGTH_SHORT).show();
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
        // Navigate to history fragment to view photos
        // This can be implemented based on your navigation structure
        Toast.makeText(requireContext(), "Opening gallery...", Toast.LENGTH_SHORT).show();
    }

    private void showPermissionLayout() {
        permissionLayout.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.GONE);
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(requireContext(),
                        "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
