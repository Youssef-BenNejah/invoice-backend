package com.harmoniedev.api.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

/** Replaces Redis for this single-process desktop deployment: TTL key/value store, in memory. */
@Service
public class InMemoryCacheService {
	private record Entry(String value, Instant expiresAt) {}

	private final Map<String, Entry> store = new ConcurrentHashMap<>();
	private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "in-memory-cache-cleaner");
		t.setDaemon(true);
		return t;
	});

	public InMemoryCacheService() {
		cleaner.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
	}

	public void set(String key, String value, Duration ttl) {
		store.put(key, new Entry(value, Instant.now().plus(ttl)));
	}

	public String get(String key) {
		Entry entry = store.get(key);
		if (entry == null) {
			return null;
		}
		if (entry.expiresAt().isBefore(Instant.now())) {
			store.remove(key);
			return null;
		}
		return entry.value();
	}

	public boolean hasKey(String key) {
		return get(key) != null;
	}

	public void delete(String key) {
		store.remove(key);
	}

	private void evictExpired() {
		Instant now = Instant.now();
		store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
	}

	@PreDestroy
	void shutdown() {
		cleaner.shutdownNow();
	}
}
