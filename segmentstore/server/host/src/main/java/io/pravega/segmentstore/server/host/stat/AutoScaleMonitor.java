/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.segmentstore.server.host.stat;

import com.google.common.annotations.VisibleForTesting;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.common.concurrent.ExecutorServiceHelpers;
import io.pravega.segmentstore.contracts.StreamSegmentStore;
import java.util.concurrent.ScheduledExecutorService;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

/**
 * Process-wide monitor for auto-scaling events. Attaches to a {@link StreamSegmentStore} and provides a {@link SegmentStatsRecorder}
 * that can be used to record segment-related statistics. All scale events are reported using a {@link EventStreamClientFactory}.
 */
@Data
public class AutoScaleMonitor implements AutoCloseable {
    private final ScheduledExecutorService executor = ExecutorServiceHelpers.newScheduledThreadPool(10, "auto-scaler");
    private final AutoScaleProcessor processor;
    @Getter
    private final SegmentStatsRecorder recorder;

    @VisibleForTesting
    public AutoScaleMonitor(@NonNull StreamSegmentStore store, @NonNull EventStreamClientFactory clientFactory,
                            @NonNull AutoScalerConfig configuration) {
        this.processor = new AutoScaleProcessor(configuration, clientFactory, this.executor);
        this.recorder = new SegmentStatsRecorderImpl(this.processor, store, this.executor);
    }

    public AutoScaleMonitor(@NonNull StreamSegmentStore store, @NonNull AutoScalerConfig configuration) {
        this.processor = new AutoScaleProcessor(configuration, this.executor);
        this.recorder = new SegmentStatsRecorderImpl(this.processor, store, this.executor);
    }

    @Override
    public void close() {
        this.recorder.close();
        this.processor.close();
        ExecutorServiceHelpers.shutdown(this.executor);
    }
}
