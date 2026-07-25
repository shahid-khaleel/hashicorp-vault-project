package com.example.vaultdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VaultDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultDemoApplication.class, args);
    }
}
