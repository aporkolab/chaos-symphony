package hu.porkolab.chaosSymphony.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication(scanBasePackages = {
    "hu.porkolab.chaosSymphony.payment",
    "hu.porkolab.chaosSymphony.common.idemp"
})
@Import({
    hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class,
    hu.porkolab.chaosSymphony.common.idemp.IdempotencyConfig.class
})
public class PaymentSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentSvcApplication.class, args);
    }
}
