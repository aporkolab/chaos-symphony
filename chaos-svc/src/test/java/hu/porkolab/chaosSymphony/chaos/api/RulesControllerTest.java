package hu.porkolab.chaosSymphony.chaos.api;

import hu.porkolab.chaosSymphony.chaos.data.RuleRepository;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
        }
)
class RulesControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RuleRepository ruleRepository;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
    }

    @Test
    void testRulesCrud() {
        // 1. Initially, there are no rules
        ResponseEntity<Map<String, ChaosRules.Rule>> getResponse = restTemplate.exchange(
                "/api/chaos/rules",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertTrue(getResponse.getBody().isEmpty());

        // 2. Create a new rule set
        ChaosRules.Rule rule1 = new ChaosRules.Rule(0.1, 0.1, 100, 0.1);
        Map<String, ChaosRules.Rule> newRules = Map.of("topic-a", rule1);

        ResponseEntity<Map<String, ChaosRules.Rule>> postResponse = restTemplate.exchange(
                "/api/chaos/rules",
                HttpMethod.POST,
                new HttpEntity<>(newRules),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, postResponse.getStatusCode());
        assertEquals(1, postResponse.getBody().size());
        assertEquals(0.1, postResponse.getBody().get("topic-a").pDrop());
        assertEquals(1, ruleRepository.count());

        // 3. Update a single rule
        ChaosRules.Rule updatedRule = new ChaosRules.Rule(0.5, 0.5, 500, 0.5);
        ResponseEntity<ChaosRules.Rule> putResponse = restTemplate.exchange(
                "/api/chaos/rules/{topic}",
                HttpMethod.PUT,
                new HttpEntity<>(updatedRule),
                ChaosRules.Rule.class,
                "topic-a"
        );
        assertEquals(HttpStatus.OK, putResponse.getStatusCode());
        assertEquals(0.5, putResponse.getBody().pDrop());

        // Verify the update
        ResponseEntity<Map<String, ChaosRules.Rule>> getAfterPutResponse = restTemplate.exchange(
                "/api/chaos/rules",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(1, getAfterPutResponse.getBody().size());
        assertEquals(0.5, getAfterPutResponse.getBody().get("topic-a").pDrop());
        assertEquals(1, ruleRepository.count());

        // 4. Delete the rule
        ResponseEntity<Map<String, ChaosRules.Rule>> deleteResponse = restTemplate.exchange(
                "/api/chaos/rules/{topic}",
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {},
                "topic-a"
        );
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertTrue(deleteResponse.getBody().isEmpty());
        assertEquals(0, ruleRepository.count());
    }

    @Test
    void testClearRules() {
        // Given: some rules exist
        ChaosRules.Rule rule1 = new ChaosRules.Rule(0.1, 0.1, 100, 0.1);
        ChaosRules.Rule rule2 = new ChaosRules.Rule(0.2, 0.2, 200, 0.2);
        Map<String, ChaosRules.Rule> newRules = Map.of("topic-a", rule1, "topic-b", rule2);
        restTemplate.postForEntity("/api/chaos/rules", newRules, Map.class);
        assertEquals(2, ruleRepository.count());

        // When: we clear all rules
        ResponseEntity<Map<String, ChaosRules.Rule>> deleteResponse = restTemplate.exchange(
                "/api/chaos/rules",
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then: all rules are gone
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertTrue(deleteResponse.getBody().isEmpty());
        assertEquals(0, ruleRepository.count());
    }
}
