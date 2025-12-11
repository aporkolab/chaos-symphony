package hu.porkolab.chaosSymphony.common.idemp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class IdempotencyConfig {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(JdbcTemplate.class)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore jdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        return new JdbcIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean({JdbcTemplate.class, IdempotencyStore.class})
    public IdempotencyStore noopIdempotencyStore() {
        return new NoopIdempotencyStore();
    }
}
