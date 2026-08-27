package com.example.the_greatest_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TheGreatestProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheGreatestProjectApplication.class, args);
        System.out.println("""

            ██╗   ██╗ █████╗ ███╗   ███╗██╗   ██╗     ██╗██╗███╗   ██╗
            ╚██╗ ██╔╝██╔══██╗████╗ ████║██║   ██║     ██║██║████╗  ██║
             ╚████╔╝ ███████║██╔████╔██║██║   ██║     ██║██║██╔██╗ ██║
              ╚██╔╝  ██╔══██║██║╚██╔╝██║██║   ██║██   ██║██║██║╚██╗██║
               ██║   ██║  ██║██║ ╚═╝ ██║╚██████╔╝╚█████╔╝██║██║ ╚████║
               ╚═╝   ╚═╝  ╚═╝╚═╝     ╚═╝ ╚═════╝  ╚════╝ ╚═╝╚═╝  ╚═══╝
              GLOBAL INTELLIGENCE HUB  ->  http://localhost:8080
            """);
    }
}
