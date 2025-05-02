package com.example.signlangapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.AdapterView;

import androidx.appcompat.app.AppCompatActivity;

public class ReverseSignActivity extends AppCompatActivity {

    private ImageView signImageView;
    private Spinner signSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reverse);

        signImageView = findViewById(R.id.signImageView);
        signSpinner = findViewById(R.id.signSpinner);

        // Set up the spinner with sign names
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sign_language_signs, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        signSpinner.setAdapter(adapter);

        // Set a listener on the spinner to update the image when a sign is selected
        signSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String selectedSign = (String) parentView.getItemAtPosition(position);
                updateSignImage(selectedSign);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing
            }
        });
    }

    // Method to update the image based on the selected sign
    private void updateSignImage(String signName) {
        int resId = 0;

        // Assign the correct image resource based on the selected sign
        switch (signName.toLowerCase()) {
            case "hello":
                resId = R.drawable.hello_sign;  // Image for "hello"
                break;
            case "i love you":
                resId = R.drawable.iloveyou_sign;  // Image for "I love you"
                break;
            case "thanks":
                resId = R.drawable.thanks_sign;  // Image for "thanks"
                break;
            default:
                resId = R.drawable.no_image_available;  // Default image if no match
                break;
        }

        // Set the image in the ImageView
        signImageView.setImageResource(resId);
    }
}
