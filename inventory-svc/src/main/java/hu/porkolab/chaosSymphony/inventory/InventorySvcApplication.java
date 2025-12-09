package hu.porkolab.chaosSymphony.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@org.springframework.context.annotation.Import(hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class)

public class InventorySvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventorySvcApplication.class, args);
    }
}
