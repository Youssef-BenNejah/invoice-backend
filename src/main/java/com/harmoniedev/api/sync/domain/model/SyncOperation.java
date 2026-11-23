package com.harmoniedev.api.sync.domain.model;

/** SAVE covers both insert and update — a receiver always applies it as an upsert. */
public enum SyncOperation {
	SAVE,
	DELETE
}
