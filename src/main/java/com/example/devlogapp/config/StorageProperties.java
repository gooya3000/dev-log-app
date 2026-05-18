package com.example.devlogapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파일 저장 경로 설정.
 * application.properties 의 devlog.storage.root 를 바인딩.
 * PLAN.md §4.1 참조.
 */
@Component
@ConfigurationProperties(prefix = "devlog.storage")
public class StorageProperties {

    /** 회고 JSON 파일 저장 루트 디렉토리. 기본: ./data/logs */
    private String root = "./data/logs";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}
