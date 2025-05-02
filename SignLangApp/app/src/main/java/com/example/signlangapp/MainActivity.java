package com.example.signlangapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    private ViewFlipper viewFlipper;
    private TextView welcomeText;
    private TextView taglineText;
    private Button yesButton;
    private Button noButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        viewFlipper = findViewById(R.id.viewFlipper);
        welcomeText = findViewById(R.id.welcomeText);
        taglineText = findViewById(R.id.taglineText);
        yesButton = findViewById(R.id.yesButton);
        noButton = findViewById(R.id.noButton);
        
        // Set up button click listeners
        yesButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SignDetectionActivity.class);
            startActivity(intent);
        });

        noButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ReverseSignActivity.class);
            startActivity(intent);
        });
        
        // Start with splash screen view
        viewFlipper.setDisplayedChild(0);
        
        // Create animations
        showWelcomeAnimation();
    }
    
    private void showWelcomeAnimation() {
        // Create fade-in animation for welcome text
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(2000);
        fadeIn.setFillAfter(true);
        welcomeText.startAnimation(fadeIn);
        
        // Create second animation for tagline with delay
        AlphaAnimation fadeInTagline = new AlphaAnimation(0.0f, 1.0f);
        fadeInTagline.setDuration(1500);
        fadeInTagline.setStartOffset(1000);
        fadeInTagline.setFillAfter(true);
        taglineText.startAnimation(fadeInTagline);
        
        // Set animation listener to proceed to next screen
        fadeInTagline.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                // Delay for 1.5 seconds after animation ends, then show the user type screen
                new Handler().postDelayed(() -> {
                    viewFlipper.setDisplayedChild(1); // Switch to the user type question view
                }, 1500);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }
}