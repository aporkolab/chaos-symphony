package hu.porkolab.chaosSymphony.dlq.api;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    public AdminClient adminClient() {
        return mock(AdminClient.class);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf().disable()
            .authorizeHttpRequests().anyRequest().permitAll()
            .build();
    }
}
