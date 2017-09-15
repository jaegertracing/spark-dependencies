package io.jaegertracing.spark.dependencies;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;

/**
 * @author Pavol Loffay
 */
public class TracersGenerator {

    public static List<Tracer> generate(int number, String collectorUrl) {
        List<Tracer> tracers = new ArrayList<>(number);
        for (int i = 0; i < number; i++) {
            tracers.add(createTracer(UUID.randomUUID().toString().replace("-", ""), collectorUrl));
        }
        return tracers;
    }

    public static Tracer createTracer(String serviceName, String collectorUrl) {
        return new com.uber.jaeger.Tracer.Builder(serviceName,
                new RemoteReporter(new HttpSender(collectorUrl + "/api/traces", 65000), 1, 100,
                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))), new ConstSampler(true))
                .build();
    }
}
