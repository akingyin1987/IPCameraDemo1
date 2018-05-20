package com.ipcamer.demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.ipcamer.demo.webcam.WebCamBean;
import com.ipcamer.demo.webcam.WebCamFinder;

import net.reecam.IpCamera;

import java.io.IOException;
import java.util.Map;

public class IndexActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);
        WebCamFinder webCamFinder = new WebCamFinder();
        try {
          Map<String,WebCamBean>  map = webCamFinder.findMap();
            for (String s : map.keySet()) {
                System.out.println("s======"+s);
                System.out.println(map.get(s).toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
