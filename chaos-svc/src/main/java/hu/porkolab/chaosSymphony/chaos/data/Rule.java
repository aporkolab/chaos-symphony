package hu.porkolab.chaosSymphony.chaos.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chaos_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {
    @Id
    private String topic;
    private double pDrop;
    private double pDup;
    private int maxDelayMs;
    private double pCorrupt;
}
