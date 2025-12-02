package hu.porkolab.chaosSymphony.shipping.outbox;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class IdempotentOutbox {
	private final Cache<String, Boolean> sent = Caffeine.newBuilder()
			.expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(100_000).build();

	public boolean markIfFirst(String key) {
		return sent.asMap().putIfAbsent(key, Boolean.TRUE) == null;
	}
}
