package com.example.devlogapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.devlogapp.config.StorageProperties;
import com.example.devlogapp.config.AdminProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, AdminProperties.class})
public class DevLogAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevLogAppApplication.class, args);
    }

}
