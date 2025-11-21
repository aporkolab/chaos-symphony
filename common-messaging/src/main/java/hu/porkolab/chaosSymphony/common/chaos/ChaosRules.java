package hu.porkolab.chaosSymphony.common.chaos;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ChaosRules {
  // topic/type -> pDrop, pDup, maxDelayMs, pCorrupt
  private final Map<String, Rule> byKey;
  public record Rule(double pDrop, double pDup, int maxDelayMs, double pCorrupt) {}
  public ChaosRules(Map<String, Rule> byKey){ this.byKey = byKey; }

  public Rule ruleFor(String topic, String type){
    Rule r = byKey.get("topic:"+topic);
    if (r != null) return r;
    return byKey.getOrDefault("type:"+type, new Rule(0,0,0,0));
  }

  public void maybeDelay(int maxMs) {
    if (maxMs <= 0) return;
    try { Thread.sleep(ThreadLocalRandom.current().nextInt(maxMs+1)); } catch (InterruptedException ignored) {}
  }
  public boolean hit(double p){ return ThreadLocalRandom.current().nextDouble() < p; }
}
