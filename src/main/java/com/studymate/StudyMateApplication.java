package com.studymate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the StudyMate Application.
 *
 * Concepts for Beginners:
 * - @SpringBootApplication: A meta-annotation combining:
 *     1. @Configuration: Tags the class as a source of bean definitions.
 *     2. @EnableAutoConfiguration: Tells Spring Boot to automatically configure beans based on classpath dependencies.
 *     3. @ComponentScan: Automatically scans and registers components, controllers, and services in this package and subpackages.
 * - SpringApplication.run(): Boots Spring's ApplicationContext and starts the embedded Tomcat web server.
 */
@SpringBootApplication
public class StudyMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyMateApplication.class, args);
    }
}
