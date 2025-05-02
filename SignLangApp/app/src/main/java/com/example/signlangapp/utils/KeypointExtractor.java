package com.example.signlangapp.utils;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;

public class KeypointExtractor {

    private final Context context;
    private HandLandmarker handLandmarker;

    public KeypointExtractor(Context context) {
        this.context = context;
        setupHandLandmarker();
    }

    private void setupHandLandmarker() {
        try {
            // Set up the HandLandmarker with MediaPipe
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public float[] extractKeypoints(Bitmap bitmap) {
        if (handLandmarker == null || bitmap == null) {
            return null;
        }

        try {
            // Convert bitmap to MPImage
            MPImage image = new BitmapImageBuilder(bitmap).build();

            // Process the image and get hand landmarks
            HandLandmarkerResult result = handLandmarker.detect(image);

            if (result.landmarks().isEmpty()) {
                return null;  // No hands detected
            }

            // Extract keypoints
            // We'll use a fixed-size array to store landmarks for up to 2 hands
            // Each hand has 21 landmarks with x, y, z coordinates (63 values per hand)
            float[] keypoints = new float[2 * 21 * 3];  // 2 hands, 21 landmarks per hand, 3 coords per landmark

            // Initialize with zeros
            for (int i = 0; i < keypoints.length; i++) {
                keypoints[i] = 0.0f;
            }

            // Fill in detected landmarks
            for (int handIndex = 0; handIndex < Math.min(2, result.landmarks().size()); handIndex++) {
                for (int landmarkIndex = 0; landmarkIndex < 21; landmarkIndex++) {
                    int baseIndex = handIndex * 21 * 3 + landmarkIndex * 3;
                    keypoints[baseIndex] = result.landmarks().get(handIndex).get(landmarkIndex).x();
                    keypoints[baseIndex + 1] = result.landmarks().get(handIndex).get(landmarkIndex).y();
                    keypoints[baseIndex + 2] = result.landmarks().get(handIndex).get(landmarkIndex).z();
                }
            }

            return keypoints;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}