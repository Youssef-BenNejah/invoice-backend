package com.harmoniedev.api.security;

import com.harmoniedev.api.common.InMemoryCacheService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {
	private static final String PREFIX = "blacklist:";
	private final InMemoryCacheService cache;
	private final JwtTokenProvider jwtTokenProvider;

	public TokenBlacklistService(InMemoryCacheService cache, JwtTokenProvider jwtTokenProvider) {
		this.cache = cache;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	public void blacklist(String token) {
		try {
			Instant expiration = jwtTokenProvider.getExpiration(token);
			Instant now = Instant.now();
			if (expiration.isBefore(now)) {
				return;
			}
			Duration ttl = Duration.between(now, expiration);
			cache.set(PREFIX + token, "1", ttl);
		} catch (Exception ex) {
			// ignore invalid tokens
		}
	}

	public boolean isBlacklisted(String token) {
		return cache.hasKey(PREFIX + token);
	}
}
