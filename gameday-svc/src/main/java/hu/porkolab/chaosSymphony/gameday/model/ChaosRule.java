package hu.porkolab.chaosSymphony.gameday.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChaosRule {
    private String id;
    private String targetTopic;
    private FaultType faultType;
    private double probability;
    private Integer delayMs;

    public enum FaultType {
        DELAY,
        DUPLICATE,
        MUTATE,
        DROP
    }
}
