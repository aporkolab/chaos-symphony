package hu.porkolab.chaosSymphony.gameday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync

public class GameDaySvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameDaySvcApplication.class, args);
    }

}
