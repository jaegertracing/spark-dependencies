/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jaegertracing.spark.dependencies.elastic;

import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

public final class LogToConsolePrinter implements Consumer<OutputFrame> {
    private final String prefix;

    public LogToConsolePrinter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void accept(OutputFrame outputFrame) {
        String message = outputFrame.getUtf8String();
        if (message != null && !message.isEmpty()) {
            System.out.print(prefix);
            System.out.print(message);
        }
    }
}
