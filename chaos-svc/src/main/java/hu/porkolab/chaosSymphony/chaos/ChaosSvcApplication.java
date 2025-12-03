package hu.porkolab.chaosSymphony.chaos;

import org.springframework.boot.SpringApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@OpenAPIDefinition(info = @Info(title = "Chaos Service API", version = "1.0", description = "API for managing chaos engineering rules and experiments."))

public class ChaosSvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChaosSvcApplication.class, args);
    }
}
