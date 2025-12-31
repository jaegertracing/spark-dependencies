/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.opensearch;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.OutputFrame;

/**
 * @author Danish Siddiqui
 */
public class LogToConsolePrinter implements Consumer<OutputFrame> {
    private static final Logger log = LoggerFactory.getLogger(LogToConsolePrinter.class);
    private final String prefix;

    public LogToConsolePrinter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void accept(OutputFrame outputFrame) {
        String utf8String = outputFrame.getUtf8String();
        if (utf8String != null) {
            System.out.print(prefix + utf8String);
        }
    }
}
