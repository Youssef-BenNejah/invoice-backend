package com.harmoniedev.api.sync;

import java.util.Set;

/**
 * Single source of truth for which Mongo collections take part in sync. Auth state ({@code users},
 * {@code refresh_tokens}) is deliberately excluded — the desktop caches its own JWT for offline
 * login instead of syncing accounts as data — along with the sync module's own bookkeeping.
 */
public final class SyncCollections {
	private static final Set<String> EXCLUDED =
			Set.of("sync_outbox", "sync_state", "sync_counters", "sync_conflicts", "users", "refresh_tokens");

	private SyncCollections() {}

	public static boolean isSyncable(String collectionName) {
		return collectionName != null && !EXCLUDED.contains(collectionName);
	}
}
