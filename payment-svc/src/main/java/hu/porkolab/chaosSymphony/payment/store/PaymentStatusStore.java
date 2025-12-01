package hu.porkolab.chaosSymphony.payment.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStatusStore {

    private final Map<String, String> statusStore = new ConcurrentHashMap<>();

    public void save(String orderId, String status) {
        statusStore.put(orderId, status);
    }

    public Optional<String> getStatus(String orderId) {
        return Optional.ofNullable(statusStore.get(orderId));
    }

    public void clear() {
        statusStore.clear();
    }
}
