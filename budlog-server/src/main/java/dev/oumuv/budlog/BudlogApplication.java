package dev.oumuv.budlog;

import dev.oumuv.budlog.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BudlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BudlogApplication.class, args);
    }
}

