package io.jaegertracing.spark.dependencies.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;


/**
 * @author Pavol Loffay
 */
public class Node {
    private List<Node> descendants = new ArrayList<>();
    private Span span;
    private String serviceName;

    public Node(Tracer tracer, Node parent) {
        this.serviceName = tracer.getServiceName();
        this.span = createSpan(tracer, parent == null ? null : parent.getSpan());
    }

    public Span getSpan() {
        return span;
    }

    public void addDescendant(Node descendant) {
        this.descendants.add(descendant);
    }

    public List<Node> getDescendants() {
        return Collections.unmodifiableList(descendants);
    }

    public String getServiceName() {
        return serviceName;
    }

    private Span createSpan(Tracer tracer, Span parent) {
        io.opentracing.Tracer.SpanBuilder spanBuilder = tracer.buildSpan(parent == null ? "|" :"->");
        if (parent != null) {
            spanBuilder.asChildOf(parent);
        }
        Span span = (Span)spanBuilder.startManual();
        if (parent != null) {
            span.setOperationName(parent.getOperationName() + "->");
        }
        return span;
    }
}
