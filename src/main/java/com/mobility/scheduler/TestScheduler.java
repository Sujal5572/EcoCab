package com.mobility.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TestScheduler {

    @Scheduled(fixedRate = 10000)
    @SchedulerLock(name = "testScheduler", lockAtMostFor = "30s")
    public void testScheduler() {
        System.out.println("🔥 Scheduler running at: " + System.currentTimeMillis());
    }
}