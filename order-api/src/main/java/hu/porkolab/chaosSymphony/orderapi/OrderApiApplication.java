package hu.porkolab.chaosSymphony.orderapi;

import org.springframework.boot.SpringApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Order API", version = "1.0", description = "API for creating and managing orders."))
@Import({
    hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class,
    hu.porkolab.chaosSymphony.common.idemp.IdempotencyConfig.class
})

public class OrderApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
