package hu.porkolab.chaosSymphony.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {
    "hu.porkolab.chaosSymphony.inventory",
    "hu.porkolab.chaosSymphony.common.idemp"
})
@Import({
    hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class,
    hu.porkolab.chaosSymphony.common.idemp.IdempotencyConfig.class
})
public class InventorySvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventorySvcApplication.class, args);
    }
}
