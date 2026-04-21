package com.mobility.common.observability;

import com.mobility.domain.demand.DemandSignal;
import com.mobility.domain.demand.DemandSignalRepository;
import com.mobility.domain.driver.Driver;
import com.mobility.domain.driver.DriverRepository;
import com.mobility.infrastructure.websocket.DemandWebSocketHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// common/observability/MetricsConfig.java
@Configuration
public class MetricsConfig {

    /**
     * Custom business metrics — beyond the default Spring Boot metrics.
     * These are the gauges that tell you the platform is healthy,
     * not just that the JVM is alive.
     */
    @Bean
    public MeterBinder mobilityMetrics(
            DriverRepository driverRepo,
            DemandSignalRepository demandRepo,
            DemandWebSocketHandler wsHandler) {

        return registry -> {
            // How many drivers are currently online across all corridors?
            Gauge.builder("mobility.drivers.online",
                            driverRepo,
                            repo -> repo.countByStatus(Driver.DriverStatus.ONLINE))
                    .description("Online drivers across all corridors")
                    .register(registry);

            // How many active demand signals are pending pickup?
            Gauge.builder("mobility.demand.active",
                            demandRepo,
                            repo -> repo.countByStatus(DemandSignal.DemandStatus.ACTIVE))
                    .description("Active unserved demand signals")
                    .register(registry);

            // WebSocket connections — watch this for memory pressure
            Gauge.builder("mobility.websocket.connections",
                            wsHandler,
                            DemandWebSocketHandler::totalConnections)
                    .description("Active WebSocket sessions")
                    .register(registry);
        };
    }
}