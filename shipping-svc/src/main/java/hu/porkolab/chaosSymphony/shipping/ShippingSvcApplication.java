package hu.porkolab.chaosSymphony.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {
    "hu.porkolab.chaosSymphony.shipping",
    "hu.porkolab.chaosSymphony.common.idemp"
})
@Import({
    hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class,
    hu.porkolab.chaosSymphony.common.idemp.IdempotencyConfig.class
})
public class ShippingSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingSvcApplication.class, args);
    }
}
