package com.example.yolov5tfliteandroid.pages;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.yolov5tfliteandroid.MainActivity;
import com.example.yolov5tfliteandroid.R;

public class HomeActivity extends AppCompatActivity {
    private Button button;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        button = (Button) findViewById(R.id.berangkatBtn);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }
}