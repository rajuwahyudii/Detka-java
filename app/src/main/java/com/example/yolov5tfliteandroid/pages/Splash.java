package com.example.yolov5tfliteandroid.pages;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.yolov5tfliteandroid.R;

public class Splash extends AppCompatActivity {

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
    startActivity(new Intent(Splash.this, Introduction.class));
    finish();

            }
        },3000);
    }
}