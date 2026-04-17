package com.hackathon.resourceallocation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResourseAllocationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourseAllocationApplication.class, args);
//        System.out.println(new BCryptPasswordEncoder().encode("admin123"));
    }
}
