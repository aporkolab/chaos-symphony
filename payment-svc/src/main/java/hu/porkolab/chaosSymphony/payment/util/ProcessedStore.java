package hu.porkolab.chaosSymphony.payment.util;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessedStore {
	private final Set<String> ids = ConcurrentHashMap.newKeySet();

	public boolean seen(String id) {
		return !ids.add(id);
	} // true = már láttuk
}
