package com.harmoniedev.api.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** In-process rate limiting (no Redis) — fine for a single-instance desktop deployment. */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod())
				|| !"/api/v1/auth/login".equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String clientIp = resolveClientIp(request);
		Bucket bucket = buckets.computeIfAbsent("login:" + clientIp, key -> Bucket.builder()
				.addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
				.build());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			response.setStatus(429);
			response.setContentType("application/problem+json");
			String body = "{"
					+ "\"type\":\"https://yourapp.com/errors/rate-limit\","
					+ "\"title\":\"Too Many Requests\","
					+ "\"status\":429,"
					+ "\"detail\":\"Too many login attempts\","
					+ "\"instance\":\"" + request.getRequestURI() + "\""
					+ "}";
			response.getWriter().write(body);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
