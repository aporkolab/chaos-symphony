package hu.porkolab.chaosSymphony.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class)

public class ShippingSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingSvcApplication.class, args);
    }
}
