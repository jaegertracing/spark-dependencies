package io.jaegertracing.spark.dependencies.common;

import brave.Tracing;
import brave.sampler.Sampler;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;
import io.jaegertracing.spark.dependencies.common.tree.TracingWrapper;
import io.jaegertracing.spark.dependencies.common.tree.TracingWrapper.JaegerWrapper;
import io.jaegertracing.spark.dependencies.common.tree.TracingWrapper.ZipkinWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;
import zipkin.reporter.okhttp3.OkHttpSender;

/**
 * @author Pavol Loffay
 */
public class TracersGenerator {

    public static class TracerServiceName<T> {
        private T tracer;
        private String serviceName;

        public TracerServiceName(T tracer, String serviceName) {
            this.tracer = tracer;
            this.serviceName = serviceName;
        }

        public T getTracer() {
            return tracer;
        }

        public TracingWrapper tracingWrapper() {
            if (tracer instanceof Tracing) {
                return new ZipkinWrapper((brave.Tracing)tracer, serviceName);
            }
            return new JaegerWrapper((com.uber.jaeger.Tracer)tracer);
        }
    }

    public static List<TracerServiceName<Tracer>> generateJaeger(int number, String collectorUrl) {
        List<TracerServiceName<Tracer>> tracers = new ArrayList<>(number);
        for (int i = 0; i < number; i++) {
            String serviceName = UUID.randomUUID().toString().replace("-", "");
            tracers.add(new TracerServiceName<>(createJaeger(serviceName, collectorUrl), serviceName));
        }
        return tracers;
    }

    public static Tracer createJaeger(String serviceName, String collectorUrl) {
        return new com.uber.jaeger.Tracer.Builder(serviceName,
                new RemoteReporter(new HttpSender(collectorUrl + "/api/traces", 65000), 1, 100,
                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))), new ConstSampler(true))
                .build();
    }

    public static List<TracerServiceName<Tracing>> generateZipkin(int number, String collectorUrl) {
        List<TracerServiceName<Tracing>> tracers = new ArrayList<>(number);
        for (int i = 0; i < number; i++) {
            String serviceName = UUID.randomUUID().toString().replace("-", "");
            tracers.add(new TracerServiceName<>(createZipkin(serviceName, collectorUrl), serviceName));
        }
        return tracers;
    }

    public static Tracing createZipkin(String serviceName, String collectorUrl) {
        Sender sender = OkHttpSender.builder()
            .endpoint(collectorUrl + "/api/v1/spans")
            .encoding(Encoding.JSON)
            .build();
        return Tracing.newBuilder()
            .localServiceName(serviceName)
            .sampler(Sampler.ALWAYS_SAMPLE)
            .traceId128Bit(true)
            .reporter(AsyncReporter.builder(sender).build())
            .build();
    }
}
