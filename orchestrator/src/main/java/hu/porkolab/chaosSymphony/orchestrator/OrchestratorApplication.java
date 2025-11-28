package hu.porkolab.chaosSymphony.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ComponentScan(basePackages = {
    "hu.porkolab.chaosSymphony.orchestrator",
    "hu.porkolab.chaosSymphony.common.idemp"
})
@EnableTransactionManagement
@EnableScheduling
@org.springframework.context.annotation.Import(hu.porkolab.chaosSymphony.common.kafka.KafkaErrorHandlingConfig.class)

public class OrchestratorApplication {
	public static void main(String[] args) {
		SpringApplication.run(OrchestratorApplication.class, args);
	}
}
