package com.urlshortener.util;

/**
 * Twitter-Snowflake-style distributed ID generator: 64-bit longs composed of a
 * millisecond timestamp, a node ID, and a per-millisecond sequence, so any number
 * of application instances can mint globally unique, roughly time-ordered IDs
 * with zero coordination (no DB round trip, no shared counter, no locks across
 * instances). Encode the result with {@link Base62Encoder} for a compact,
 * URL-safe short code.
 *
 * <p>Bit layout (MSB to LSB), 63 usable bits (sign bit left 0):
 * <pre>
 * 0 | 41 bits: ms since custom epoch | 10 bits: node ID (0-1023) | 12 bits: sequence (0-4095)
 * </pre>
 * Capacity: up to 4096 IDs/ms per node, 1024 nodes, ~69 years of range from the epoch.
 */
public final class SnowflakeIdGenerator {

	/** 2026-01-01T00:00:00Z, chosen so early IDs (and their Base62 codes) stay as short as possible. */
	private static final long DEFAULT_EPOCH_MILLIS = 1_767_225_600_000L;

	private static final int NODE_ID_BITS = 10;
	private static final int SEQUENCE_BITS = 12;
	private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
	private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
	private static final int NODE_ID_SHIFT = SEQUENCE_BITS;
	private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

	private final long epochMillis;
	private final long nodeId;

	private long lastTimestamp = -1L;
	private long sequence = 0L;

	public SnowflakeIdGenerator(long nodeId) {
		this(nodeId, DEFAULT_EPOCH_MILLIS);
	}

	public SnowflakeIdGenerator(long nodeId, long epochMillis) {
		if (nodeId < 0 || nodeId > MAX_NODE_ID) {
			throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID + ": " + nodeId);
		}
		this.nodeId = nodeId;
		this.epochMillis = epochMillis;
	}

	/** Generates the next unique, non-negative, roughly time-ordered ID. Thread-safe. */
	public synchronized long nextId() {
		long timestamp = currentTimeMillis();

		if (timestamp < lastTimestamp) {
			// Clock moved backwards (NTP adjustment, VM migration) - refuse rather than risk a duplicate ID.
			throw new IllegalStateException(
					"Clock moved backwards by " + (lastTimestamp - timestamp) + "ms; refusing to generate an ID");
		}

		if (timestamp == lastTimestamp) {
			sequence = (sequence + 1) & MAX_SEQUENCE;
			if (sequence == 0) {
				timestamp = waitForNextMillis(lastTimestamp);
			}
		} else {
			sequence = 0L;
		}
		lastTimestamp = timestamp;

		return ((timestamp - epochMillis) << TIMESTAMP_SHIFT) | (nodeId << NODE_ID_SHIFT) | sequence;
	}

	private long waitForNextMillis(long currentLastTimestamp) {
		long timestamp = currentTimeMillis();
		while (timestamp <= currentLastTimestamp) {
			timestamp = currentTimeMillis();
		}
		return timestamp;
	}

	long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
