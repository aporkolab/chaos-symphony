package hu.porkolab.chaosSymphony.dlq;

import org.springframework.boot.SpringApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "DLQ Admin API", version = "1.0", description = "API for managing Dead Letter Queues."))

public class DlqAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(DlqAdminApplication.class, args);
    }
}
