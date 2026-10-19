package com.studyplanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class StudyPlannerApplicationTest {

    @Test
    void contextLoads() {
        try (ConfigurableApplicationContext context = SpringApplication.run(
                StudyPlannerApplication.class,
                "--spring.main.web-application-type=none")) {
            org.assertj.core.api.Assertions.assertThat(context.isActive()).isTrue();
        }
    }
}
