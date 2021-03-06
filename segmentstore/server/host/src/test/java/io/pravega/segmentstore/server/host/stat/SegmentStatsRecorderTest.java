/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.segmentstore.server.host.stat;

import io.pravega.common.util.ImmutableDate;
import io.pravega.segmentstore.contracts.Attributes;
import io.pravega.segmentstore.contracts.SegmentProperties;
import io.pravega.segmentstore.contracts.StreamSegmentStore;
import io.pravega.shared.protocol.netty.WireCommands;
import io.pravega.test.common.ThreadPooledTestSuite;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SegmentStatsRecorderTest extends ThreadPooledTestSuite {
    private static final String STREAM_SEGMENT_NAME = "test/test/0";

    private SegmentStatsRecorderImpl statsRecorder;
    private final CompletableFuture<Void> latch = new CompletableFuture<>();

    protected int getThreadPoolSize() {
        return 3;
    }

    @Before
    public void setup() {
        AutoScaleProcessor processor = mock(AutoScaleProcessor.class);
        StreamSegmentStore store = mock(StreamSegmentStore.class);
        CompletableFuture<SegmentProperties> toBeReturned = CompletableFuture.completedFuture(new SegmentProperties() {
            @Override
            public String getName() {
                return STREAM_SEGMENT_NAME;
            }

            @Override
            public boolean isSealed() {
                return false;
            }

            @Override
            public boolean isDeleted() {
                return false;
            }

            @Override
            public ImmutableDate getLastModified() {
                return null;
            }

            @Override
            public long getStartOffset() {
                return 0;
            }

            @Override
            public long getLength() {
                return 0;
            }

            @Override
            public Map<UUID, Long> getAttributes() {
                Map<UUID, Long> map = new HashMap<>();
                map.put(Attributes.SCALE_POLICY_TYPE, 0L);
                map.put(Attributes.SCALE_POLICY_RATE, 10L);
                latch.complete(null);
                return map;
            }
        });

        when(store.getStreamSegmentInfo(STREAM_SEGMENT_NAME, Duration.ofMinutes(1))).thenReturn(toBeReturned);

        statsRecorder = new SegmentStatsRecorderImpl(processor, store, Duration.ofSeconds(10000), Duration.ofSeconds(2), executorService());
    }

    @Test(timeout = 10000)
    public void testRecordTraffic() {
        statsRecorder.createSegment(STREAM_SEGMENT_NAME, WireCommands.CreateSegment.IN_EVENTS_PER_SEC, 10);

        assertEquals(0, (int) statsRecorder.getIfPresent(STREAM_SEGMENT_NAME).getTwoMinuteRate());
        // record for over 5 seconds
        long startTime = System.currentTimeMillis();
        // after 10 seconds we should have written ~100 events.
        // Which means 2 minute rate at this point is 100 / 120 ~= 0.4 events per second
        while (System.currentTimeMillis() - startTime < Duration.ofSeconds(6).toMillis()) {
            for (int i = 0; i < 11; i++) {
                statsRecorder.record(STREAM_SEGMENT_NAME, 0, 1);
            }
        }
        assertTrue(statsRecorder.getIfPresent(STREAM_SEGMENT_NAME).getTwoMinuteRate() > 0);
    }

    @Test(timeout = 10000)
    public void testExpireSegment() throws InterruptedException, ExecutionException {
        statsRecorder.createSegment(STREAM_SEGMENT_NAME, WireCommands.CreateSegment.IN_EVENTS_PER_SEC, 10);

        assertNotNull(statsRecorder.getIfPresent(STREAM_SEGMENT_NAME));
        Thread.sleep(2500);
        // Verify that segment has been removed from the cache
        assertNull(statsRecorder.getIfPresent(STREAM_SEGMENT_NAME));

        // this should result in asynchronous loading of STREAM_SEGMENT_NAME
        statsRecorder.record(STREAM_SEGMENT_NAME, 0, 1);
        latch.get();
        int i = 20;
        while (statsRecorder.getIfPresent(STREAM_SEGMENT_NAME) == null && i-- != 0) {
            Thread.sleep(100);
        }
        assertNotNull(statsRecorder.getIfPresent(STREAM_SEGMENT_NAME));
    }
}
