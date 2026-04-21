package com.mobility;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// FIX: 3 annotations were missing:
// @EnableScheduling   → @Scheduled methods never fire without this
// @EnableJpaAuditing  → @CreatedDate/@LastModifiedDate never populate without this
// @EnableSchedulerLock → throws NoSuchBeanDefinitionException without this
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableSchedulerLock(defaultLockAtMostFor = "25s")
public class MobilityPlatformApplication {
	public static void main(String[] args) {
		SpringApplication.run(MobilityPlatformApplication.class, args);
	}
}