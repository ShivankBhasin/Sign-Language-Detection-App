package com.example.signlangapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.signlangapp.utils.KeypointExtractor;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SignDetectionActivity extends AppCompatActivity {

    private static final String TAG = "SignDetectionActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    
    private PreviewView previewView;
    private Button buttonStartStop;
    private Button buttonBack;
    private Button buttonSpeak;
    private TextView textViewResult;
    private TextView textViewSentence;
    
    private boolean isDetecting = false;
    private Executor executor = Executors.newSingleThreadExecutor();
    private TextToSpeech tts;
    private ProcessCameraProvider cameraProvider;
    
    // ML components
    private Interpreter tflite;
    private KeypointExtractor keypointExtractor;
    
    // For sign detection
    private List<String> detectedSigns = new ArrayList<>();
    private String[] actions = {"Hello", "Thanks", "I love you"};
    private int sameSignCount = 0;
    private String lastPredictedAction = null;
    
    // For prediction stability
    private long lastAddedTime = 0;
    private static final long DELAY_BETWEEN_SIGNS = 10000; // 10 seconds between adding signs
    private int requiredConsecutiveDetections = 3; // Need to detect same sign this many times in a row
    private boolean waitingForNewSign = false;
    
    // Analysis control
    private final Handler mainHandler = new Handler();
    private int frameCounter = 0;
    private static final int ANALYZE_EVERY_N_FRAMES = 5; // Process every 5th frame

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection);
        Log.d(TAG, "onCreate called");

        try {
            // Initialize UI elements
            previewView = findViewById(R.id.cameraView);
            buttonStartStop = findViewById(R.id.buttonStartStop);
            buttonBack = findViewById(R.id.buttonBack);
            buttonSpeak = findViewById(R.id.buttonSpeak);
            textViewResult = findViewById(R.id.predictionText);
            textViewSentence = findViewById(R.id.textViewSentence);

            // Initialize Text-to-Speech
            initializeTTS();
            
            // Initialize KeypointExtractor
            try {
                keypointExtractor = new KeypointExtractor(this);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing KeypointExtractor", e);
            }

            // Set click listeners
            buttonStartStop.setOnClickListener(v -> {
                if (isDetecting) {
                    stopDetection();
                } else {
                    startDetection();
                }
            });

            buttonBack.setOnClickListener(v -> {
                onBackPressed();
            });

            buttonSpeak.setOnClickListener(v -> {
                speakSentence();
            });

            // Check for camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                startCamera();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });
    }

    private boolean verifyModelFile(String filename) {
        try {
            String[] assets = getAssets().list("");
            for (String asset : assets) {
                if (filename.equals(asset)) {
                    Log.d(TAG, "Found model file: " + filename);
                    return true;
                }
            }
            Log.e(TAG, "Model file not found: " + filename);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Error verifying model file: " + e.getMessage());
            return false;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview() {
        if (cameraProvider == null) {
            return;
        }

        // Set up the preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Select front camera as default
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        try {
            // Unbind any bound use cases before rebinding
            cameraProvider.unbindAll();
            
            // Bind the preview use case to the camera
            cameraProvider.bindToLifecycle(this, cameraSelector, preview);
            
        } catch (Exception e) {
            Log.e(TAG, "Error binding preview", e);
            Toast.makeText(this, "Error setting up camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDetection() {
        if (cameraProvider == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        isDetecting = true;
        buttonStartStop.setText(R.string.stop_detection);
        textViewResult.setText("Detecting...");
        
        // Reset detection state
        sameSignCount = 0;
        lastPredictedAction = null;
        waitingForNewSign = false;
        lastAddedTime = 0;

        // Setup image analysis
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, imageProxy -> {
            try {
                // Control analysis frequency for better performance
                frameCounter = (frameCounter + 1) % ANALYZE_EVERY_N_FRAMES;
                if (frameCounter != 0) {
                    imageProxy.close();
                    return;
                }
                
                // Don't process if we're in waiting period after adding a sign
                long currentTime = System.currentTimeMillis();
                if (waitingForNewSign && (currentTime - lastAddedTime < DELAY_BETWEEN_SIGNS)) {
                    // Still in cooldown period
                    long remaining = (lastAddedTime + DELAY_BETWEEN_SIGNS - currentTime) / 1000;
                    final long secondsRemaining = remaining;
                    runOnUiThread(() -> {
                        textViewResult.setText("Next detection in " + secondsRemaining + "s");
                    });
                    imageProxy.close();
                    return;
                } else if (waitingForNewSign) {
                    // Cooldown period over
                    waitingForNewSign = false;
                    sameSignCount = 0;
                    lastPredictedAction = null;
                    runOnUiThread(() -> {
                        textViewResult.setText("Ready for next sign");
                    });
                }
                
                Bitmap bitmap = imageToBitmap(imageProxy);
                if (bitmap == null) {
                    imageProxy.close();
                    return;
                }
                
                // Process the frame to detect signs
                if (keypointExtractor != null) {
                    try {
                        float[] keypoints = keypointExtractor.extractKeypoints(bitmap);
                        
                        // Only proceed if we have valid hand keypoints
                        if (keypoints != null && hasEnoughKeypoints(keypoints)) {
                            detectSignFromKeypoints(keypoints);
                        } else {
                            // No valid hand detected
                            resetDetection("No hand detected");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Keypoint extraction error", e);
                        // Fall back to simpler detection if MediaPipe fails
                        detectSignFromImage(bitmap);
                    }
                } else {
                    // No MediaPipe available, use image analysis
                    detectSignFromImage(bitmap);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Frame analysis error", e);
            } finally {
                imageProxy.close();
            }
        });

        try {
            // Select front camera
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();

            // Unbind previous use cases
            cameraProvider.unbindAll();

            // Set up the preview
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Bind both preview and analysis to camera
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            
        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases", e);
            stopDetection();
        }
    }

    private boolean hasEnoughKeypoints(float[] keypoints) {
        if (keypoints == null) return false;
        
        int validPoints = 0;
        for (int i = 0; i < keypoints.length; i += 3) {
            if (i + 2 < keypoints.length) {
                if (Math.abs(keypoints[i]) > 0.01f || 
                    Math.abs(keypoints[i+1]) > 0.01f ||
                    Math.abs(keypoints[i+2]) > 0.01f) {
                    validPoints++;
                }
            }
        }
        
        return validPoints >= 15; // Need at least 15 valid points (out of 21 hand landmarks)
    }
    
    private void detectSignFromKeypoints(float[] keypoints) {
        String predictedSign = null;
        
        // First, detect by hand shape/gesture
        float[] handAngles = calculateHandAngles(keypoints);
        
        // Check for "Hello" sign - fingers spread, often in waving position
        if (areFingersStraightAndSpread(keypoints)) {
            predictedSign = actions[0]; // "Hello"
        } 
        // Check for "Thanks" - flat hand with fingers together
        else if (areFingersStraightAndTogether(keypoints)) {
            predictedSign = actions[1]; // "Thanks"
        }
        // Check for "I love you" - thumb, index, and pinky extended
        else if (isILoveYouSign(keypoints)) {
            predictedSign = actions[2]; // "I love you"
        }
        
        // If gesture detection failed, fall back to position-based detection
        if (predictedSign == null) {
            float avgX = calculateAverageXPosition(keypoints);
            
            if (avgX < 0.4f) {
                predictedSign = actions[0]; // "Hello"
            } else if (avgX > 0.6f) {
                predictedSign = actions[2]; // "I love you"
            } else {
                predictedSign = actions[1]; // "Thanks"
            }
        }
        
        if (predictedSign != null) {
            processSignPrediction(predictedSign);
        }
    }
    
    private float[] calculateHandAngles(float[] keypoints) {
        // Calculate angles between finger joints for better gesture recognition
        // Typically needs 21 landmarks x 3 coordinates = 63 values in keypoints array
        float[] angles = new float[5]; // One angle per finger
        // This calculation would be complex and depends on the exact keypoint format
        // Placeholder implementation
        return angles;
    }
    
    private boolean areFingersStraightAndSpread(float[] keypoints) {
        // Check if fingers are extended and spread apart
        // This detection is for "Hello" sign
        
        if (keypoints.length < 63) return false; // Need enough points
        
        // Extract fingertip positions
        float indexTipX = keypoints[8*3];
        float indexTipY = keypoints[8*3 + 1];
        float middleTipX = keypoints[12*3];
        float middleTipY = keypoints[12*3 + 1];
        float ringTipX = keypoints[16*3];
        float ringTipY = keypoints[16*3 + 1];
        float pinkyTipX = keypoints[20*3];
        float pinkyTipY = keypoints[20*3 + 1];
        
        // Extract wrist position
        float wristX = keypoints[0];
        float wristY = keypoints[1];
        
        // Calculate distances from fingertips to wrist
        float indexDist = distance(indexTipX, indexTipY, wristX, wristY);
        float middleDist = distance(middleTipX, middleTipY, wristX, wristY);
        float ringDist = distance(ringTipX, ringTipY, wristX, wristY);
        float pinkyDist = distance(pinkyTipX, pinkyTipY, wristX, wristY);
        
        // Calculate distances between adjacent fingertips
        float indexMiddleDist = distance(indexTipX, indexTipY, middleTipX, middleTipY);
        float middleRingDist = distance(middleTipX, middleTipY, ringTipX, ringTipY);
        float ringPinkyDist = distance(ringTipX, ringTipY, pinkyTipX, pinkyTipY);
        
        // Check if fingers are extended (far from wrist)
        boolean fingersExtended = 
            indexDist > 0.15f && 
            middleDist > 0.15f &&
            ringDist > 0.15f &&
            pinkyDist > 0.15f;
            
        // Check if fingers are spread apart
        boolean fingersSpread = 
            indexMiddleDist > 0.03f &&
            middleRingDist > 0.03f &&
            ringPinkyDist > 0.03f;
            
        return fingersExtended && fingersSpread;
    }
    
    private boolean areFingersStraightAndTogether(float[] keypoints) {
        // Check if fingers are extended but close together
        // This detection is for "Thanks" sign
        
        if (keypoints.length < 63) return false;
        
        // Extract fingertip positions
        float indexTipX = keypoints[8*3];
        float indexTipY = keypoints[8*3 + 1];
        float middleTipX = keypoints[12*3];
        float middleTipY = keypoints[12*3 + 1];
        float ringTipX = keypoints[16*3];
        float ringTipY = keypoints[16*3 + 1];
        float pinkyTipX = keypoints[20*3];
        float pinkyTipY = keypoints[20*3 + 1];
        
        // Extract wrist position
        float wristX = keypoints[0];
        float wristY = keypoints[1];
        
        // Calculate distances from fingertips to wrist
        float indexDist = distance(indexTipX, indexTipY, wristX, wristY);
        float middleDist = distance(middleTipX, middleTipY, wristX, wristY);
        float ringDist = distance(ringTipX, ringTipY, wristX, wristY);
        float pinkyDist = distance(pinkyTipX, pinkyTipY, wristX, wristY);
        
        // Calculate distances between adjacent fingertips
        float indexMiddleDist = distance(indexTipX, indexTipY, middleTipX, middleTipY);
        float middleRingDist = distance(middleTipX, middleTipY, ringTipX, ringTipY);
        float ringPinkyDist = distance(ringTipX, ringTipY, pinkyTipX, pinkyTipY);
        
        // Check if fingers are extended (far from wrist)
        boolean fingersExtended = 
            indexDist > 0.15f && 
            middleDist > 0.15f &&
            ringDist > 0.15f &&
            pinkyDist > 0.15f;
            
        // Check if fingers are close together
        boolean fingersTogether = 
            indexMiddleDist < 0.05f &&
            middleRingDist < 0.05f &&
            ringPinkyDist < 0.05f;
            
        return fingersExtended && fingersTogether;
    }
    
    private boolean isILoveYouSign(float[] keypoints) {
        // Check for the "I love you" sign: thumb, index, and pinky extended, middle and ring curled
        
        if (keypoints.length < 63) return false;
        
        // Extract fingertip positions
        float thumbTipX = keypoints[4*3];
        float thumbTipY = keypoints[4*3 + 1];
        float indexTipX = keypoints[8*3];
        float indexTipY = keypoints[8*3 + 1];
        float middleTipX = keypoints[12*3];
        float middleTipY = keypoints[12*3 + 1];
        float ringTipX = keypoints[16*3];
        float ringTipY = keypoints[16*3 + 1];
        float pinkyTipX = keypoints[20*3];
        float pinkyTipY = keypoints[20*3 + 1];
        
        // Get middle joints for comparison
        float indexMidX = keypoints[6*3];
        float indexMidY = keypoints[6*3 + 1];
        float middleMidX = keypoints[10*3];
        float middleMidY = keypoints[10*3 + 1];
        float ringMidX = keypoints[14*3];
        float ringMidY = keypoints[14*3 + 1];
        float pinkyMidX = keypoints[18*3];
        float pinkyMidY = keypoints[18*3 + 1];
        
        // Extract wrist position
        float wristX = keypoints[0];
        float wristY = keypoints[1];
        
        // Check thumb extension (distance from wrist to tip)
        float thumbDist = distance(thumbTipX, thumbTipY, wristX, wristY);
        
        // Check index extension
        boolean indexExtended = distance(indexTipX, indexTipY, wristX, wristY) > 
                                distance(indexMidX, indexMidY, wristX, wristY);
                                
        // Check pinky extension
        boolean pinkyExtended = distance(pinkyTipX, pinkyTipY, wristX, wristY) > 
                                distance(pinkyMidX, pinkyMidY, wristX, wristY);
                                
        // Check middle and ring finger curl (closer to palm)
        boolean middleCurled = distance(middleTipX, middleTipY, wristX, wristY) < 
                              distance(middleMidX, middleMidY, wristX, wristY);
                              
        boolean ringCurled = distance(ringTipX, ringTipY, wristX, wristY) < 
                            distance(ringMidX, ringMidY, wristX, wristY);
        
        // The "I love you" sign has thumb, index, and pinky extended with middle and ring curled
        return thumbDist > 0.1f && indexExtended && pinkyExtended && middleCurled && ringCurled;
    }
    
    private float calculateAverageXPosition(float[] keypoints) {
        float sumX = 0;
        int count = 0;
        
        for (int i = 0; i < keypoints.length; i += 3) {
            if (i < keypoints.length && Math.abs(keypoints[i]) > 0.01f) {
                sumX += keypoints[i];
                count++;
            }
        }
        
        return count > 0 ? sumX / count : 0.5f;
    }
    
    private void detectSignFromImage(Bitmap bitmap) {
        // Simple image-based detection as a fallback
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Sample regions to find where there's most movement
        int leftActivity = calculateRegionActivity(bitmap, 0, 0, width/3, height);
        int centerActivity = calculateRegionActivity(bitmap, width/3, 0, 2*width/3, height);
        int rightActivity = calculateRegionActivity(bitmap, 2*width/3, 0, width, height);
        
        // Find region with highest activity
        int maxActivity = Math.max(Math.max(leftActivity, centerActivity), rightActivity);
        
        // Need minimum threshold of activity to detect a sign
        if (maxActivity < 20) {
            resetDetection("No hand detected");
            return;
        }
        
        String predictedSign;
        if (leftActivity == maxActivity) {
            predictedSign = actions[0]; // "Hello"
        } else if (rightActivity == maxActivity) {
            predictedSign = actions[2]; // "I love you"
        } else {
            predictedSign = actions[1]; // "Thanks"
        }
        
        processSignPrediction(predictedSign);
    }
    
    private int calculateRegionActivity(Bitmap bitmap, int startX, int startY, int endX, int endY) {
        // Calculate a measure of activity in the region (simplified)
        int sampleCount = 0;
        int sumBrightness = 0;
        
        for (int y = startY; y < endY; y += 20) {
            for (int x = startX; x < endX; x += 20) {
                if (x < bitmap.getWidth() && y < bitmap.getHeight()) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;
                    sumBrightness += (r + g + b) / 3;
                    sampleCount++;
                }
            }
        }
        
        if (sampleCount == 0) return 0;
        return sumBrightness / sampleCount;
    }
    
    private void processSignPrediction(String prediction) {
        if (prediction == null) return;
        
        // If we're in the waiting period, don't process new predictions
        if (waitingForNewSign) {
            return;
        }
        
        // Handle consecutive detection count
        if (lastPredictedAction != null && prediction.equals(lastPredictedAction)) {
            sameSignCount++;
        } else {
            sameSignCount = 1;
            lastPredictedAction = prediction;
        }
        
        final String currentPrediction = prediction;
        final int consecutiveCount = sameSignCount;
        
        runOnUiThread(() -> {
            // Just show the sign name
            textViewResult.setText(currentPrediction);
            
            // If we have enough consecutive detections, add to sentence
            if (consecutiveCount >= requiredConsecutiveDetections) {
                // Add to sentence
                detectedSigns.add(currentPrediction);
                updateSentence();
                
                // Record time and start waiting period
                lastAddedTime = System.currentTimeMillis();
                waitingForNewSign = true;
                
                // Reset detection state
                sameSignCount = 0;
                lastPredictedAction = null;
                
                // Start countdown timer
                startCountdownTimer();
                
                // Provide feedback
                Toast.makeText(SignDetectionActivity.this, 
                    "Added: " + currentPrediction, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void startCountdownTimer() {
        mainHandler.post(new Runnable() {
            long startTime = System.currentTimeMillis();
            
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - startTime;
                
                if (elapsedTime < DELAY_BETWEEN_SIGNS) {
                    // Update countdown display
                    long secondsLeft = (DELAY_BETWEEN_SIGNS - elapsedTime) / 1000 + 1;
                    textViewResult.setText("Next detection in " + secondsLeft + "s");
                    
                    // Schedule next update
                    mainHandler.postDelayed(this, 1000);
                } else {
                    // Countdown finished
                    waitingForNewSign = false;
                    textViewResult.setText("Ready for next sign");
                }
            }
        });
    }

    private void resetDetection(String message) {
        if (!waitingForNewSign) {
            runOnUiThread(() -> {
                if (lastPredictedAction == null) { // Only update if we don't have a prediction
                    textViewResult.setText(message);
                }
            });
            sameSignCount = 0;
            lastPredictedAction = null;
        }
    }
    
    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    private void stopDetection() {
        isDetecting = false;
        buttonStartStop.setText(R.string.start_detection);
        textViewResult.setText("");
        sameSignCount = 0;
        lastPredictedAction = null;
        waitingForNewSign = false;
        
        // Remove pending callbacks
        mainHandler.removeCallbacksAndMessages(null);

        // Just rebind the preview
        if (cameraProvider != null) {
            bindPreview();
        }
    }

    private void updateSentence() {
        StringBuilder sb = new StringBuilder();
        for (String sign : detectedSigns) {
            sb.append(sign).append(" ");
        }
        String sentence = sb.toString().trim();
        textViewSentence.setText(sentence);
    }

    private void speakSentence() {
        if (tts == null) {
            Toast.makeText(this, "Text-to-Speech not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String sentence = textViewSentence.getText().toString();
        if (!sentence.isEmpty()) {
            tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Toast.makeText(this, "No sentence to speak", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap imageToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) return null;

            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            // Rotate the bitmap if needed
            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to bitmap", e);
            return null;
        }
    }

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(getAssets().openFd(modelName).getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = getAssets().openFd(modelName).getStartOffset();
            long declaredLength = getAssets().openFd(modelName).getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (Exception e) {
            Log.e(TAG, "Error loading model file: " + modelName, e);
            throw e;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (tflite != null) {
            tflite.close();
        }
        // Remove all pending callbacks to prevent leaks
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(SignDetectionActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}