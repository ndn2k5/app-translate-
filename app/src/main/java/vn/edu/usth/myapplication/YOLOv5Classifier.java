/*
 * Copyright (c) 2025 Android project OpenVision API
 * All rights reserved.
 * Project: My Application
 * File: YOLOv5Classifier.java
 * Last Modified: 5/10/2025 5:27
 */

package vn.edu.usth.myapplication;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class YOLOv5Classifier {

    private static final String TAG = "YOLOv5Classifier";
    private final Interpreter interpreter;
    private final int inputSize = 640;
    private final List<String> labels = new ArrayList<>();

    public YOLOv5Classifier(AssetManager assetManager, String modelName) throws IOException {
        interpreter = new Interpreter(loadModelFile(assetManager, modelName));
        loadLabels(assetManager, "labels.txt");
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void loadLabels(AssetManager assetManager, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot read labels.txt", e);
        }
    }

    public List<Result> detect(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        ByteBuffer inputBuffer = preprocessBitmap(resized);

        float[][][] output = new float[1][25200][85];
        interpreter.run(inputBuffer, output);

        return postprocess(output, bitmap.getWidth(), bitmap.getHeight());
    }

    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize);
        int pixel = 0;
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.f);
                byteBuffer.putFloat((val & 0xFF) / 255.f);
            }
        }
        return byteBuffer;
    }

    private List<Result> postprocess(float[][][] output, int origW, int origH) {
        List<Result> results = new ArrayList<>();

        for (int i = 0; i < 25200; i++) {
            float[] row = output[0][i];
            float conf = row[4];
            if (conf < 0.3f) continue; // Lower threshold

            int classId = -1;
            float maxProb = 0;
            for (int c = 5; c < 85; c++) {
                if (row[c] > maxProb) {
                    maxProb = row[c];
                    classId = c - 5;
                }
            }

            float finalConf = conf * maxProb; // Combine confidence
            if (finalConf < 0.3f) continue; // Lower threshold

            if (classId >= 0 && classId < labels.size()) {
                float x = row[0];
                float y = row[1];
                float w = row[2];
                float h = row[3];

                float left = (x - w / 2) / inputSize * origW;
                float top = (y - h / 2) / inputSize * origH;
                float right = (x + w / 2) / inputSize * origW;
                float bottom = (y + h / 2) / inputSize * origH;

                results.add(new Result(labels.get(classId), finalConf, left, top, right, bottom));
            }
        }

        // Sort by confidence and apply simpler NMS
        results.sort((a, b) -> Float.compare(b.conf, a.conf));

        List<Result> filtered = new ArrayList<>();
        boolean[] suppressed = new boolean[results.size()];

        for (int i = 0; i < results.size() && filtered.size() < 10; i++) {
            if (suppressed[i]) continue;
            filtered.add(results.get(i));

            // Suppress similar detections
            for (int j = i + 1; j < results.size(); j++) {
                if (suppressed[j]) continue;
                if (results.get(i).label.equals(results.get(j).label) &&
                        calculateIoU(results.get(i), results.get(j)) > 0.3f) {
                    suppressed[j] = true;
                }
            }
        }

        Log.d(TAG, "Detected " + filtered.size() + " objects after filtering");
        return filtered;
    }

    private List<Result> applyNMS(List<Result> results, float iouThreshold) {
        if (results.isEmpty()) return results;

        // Sort by confidence
        results.sort((a, b) -> Float.compare(b.conf, a.conf));

        List<Result> selected = new ArrayList<>();
        boolean[] suppressed = new boolean[results.size()];

        for (int i = 0; i < results.size(); i++) {
            if (suppressed[i]) continue;
            selected.add(results.get(i));

            for (int j = i + 1; j < results.size(); j++) {
                if (suppressed[j]) continue;
                if (calculateIoU(results.get(i), results.get(j)) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }

        return selected;
    }

    private float calculateIoU(Result a, Result b) {
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);

        float intersectLeft = Math.max(a.left, b.left);
        float intersectTop = Math.max(a.top, b.top);
        float intersectRight = Math.min(a.right, b.right);
        float intersectBottom = Math.min(a.bottom, b.bottom);

        if (intersectRight < intersectLeft || intersectBottom < intersectTop) {
            return 0;
        }

        float intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop);
        float unionArea = areaA + areaB - intersectArea;

        return intersectArea / unionArea;
    }

    public Bitmap drawDetections(Bitmap bitmap, List<Result> results) {
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(40);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        for (Result r : results) {
            canvas.drawRect(r.left, r.top, r.right, r.bottom, paint);
            canvas.drawText(r.label + " " + String.format("%.1f%%", r.conf * 100), r.left, r.top - 10, textPaint);
        }
        return mutable;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    public static class Result {
        public final String label;
        public final float conf;
        public final float left, top, right, bottom;

        public Result(String label, float conf, float left, float top, float right, float bottom) {
            this.label = label;
            this.conf = conf;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
