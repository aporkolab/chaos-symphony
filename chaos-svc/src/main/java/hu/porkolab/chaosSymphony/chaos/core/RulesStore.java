package hu.porkolab.chaosSymphony.chaos.core;

import hu.porkolab.chaosSymphony.chaos.data.Rule;
import hu.porkolab.chaosSymphony.chaos.data.RuleRepository;
import hu.porkolab.chaosSymphony.common.chaos.ChaosRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RulesStore {

    private final RuleRepository repository;

    public Map<String, ChaosRules.Rule> get() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(
                        Rule::getTopic,
                        rule -> new ChaosRules.Rule(
                                rule.getPDrop(),
                                rule.getPDup(),
                                rule.getMaxDelayMs(),
                                rule.getPCorrupt()
                        )
                ));
    }

    @Transactional
    public Map<String, ChaosRules.Rule> set(Map<String, ChaosRules.Rule> newRules) {
        repository.deleteAll();
        if (newRules != null && !newRules.isEmpty()) {
            var entities = newRules.entrySet().stream()
                    .map(entry -> {
                        ChaosRules.Rule sourceRule = entry.getValue();
                        return Rule.builder()
                                .topic(entry.getKey())
                                .pDrop(sourceRule.pDrop())
                                .pDup(sourceRule.pDup())
                                .maxDelayMs(sourceRule.maxDelayMs())
                                .pCorrupt(sourceRule.pCorrupt())
                                .build();
                    })
                    .collect(Collectors.toList());
            repository.saveAll(entities);
        }
        return get();
    }
}
