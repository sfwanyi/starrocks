// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.
package com.starrocks.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.internal.TemporaryBuffers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

public class TraceManager {
    private static final String SERVICE_NAME = "starrocks-fe";
    private static Tracer instance = null;

    public static Tracer getTracer() {
        if (instance == null) {
            synchronized (TraceManager.class) {
                if (instance == null) {
                    if (!Config.jaeger_grpc_endpoint.isEmpty()) {
                        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder();
                        SpanProcessor processor = BatchSpanProcessor.builder(
                                JaegerGrpcSpanExporter.builder().setEndpoint(Config.jaeger_grpc_endpoint)
                                        .build()).build();
                        Resource resource = Resource.builder().put("service.name", SERVICE_NAME).build();
                        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(processor)
                                .setResource(resource)
                                .build();
                        builder.setTracerProvider(sdkTracerProvider);
                        OpenTelemetry openTelemetry = builder.buildAndRegisterGlobal();
                        instance = openTelemetry.getTracer(SERVICE_NAME);
                    } else {
                        instance = GlobalOpenTelemetry.get().getTracer(SERVICE_NAME);
                    }
                }
            }
        }
        return instance;
    }

    public static Span startSpan(String name, Span parent) {
        return getTracer().spanBuilder(name)
                .setParent(Context.current().with(parent)).startSpan();
    }

    public static Span startSpan(String name) {
        return getTracer().spanBuilder(name).startSpan();
    }

    public static String toTraceParent(SpanContext spanContext) {
        if (!spanContext.isValid()) {
            return null;
        }
        char[] chars = TemporaryBuffers.chars(55);
        chars[0] = "00".charAt(0);
        chars[1] = "00".charAt(1);
        chars[2] = '-';
        String traceId = spanContext.getTraceId();
        traceId.getChars(0, traceId.length(), chars, 3);
        chars[35] = '-';
        String spanId = spanContext.getSpanId();
        spanId.getChars(0, spanId.length(), chars, 36);
        chars[52] = '-';
        String traceFlagsHex = spanContext.getTraceFlags().asHex();
        chars[53] = traceFlagsHex.charAt(0);
        chars[54] = traceFlagsHex.charAt(1);
        return new String(chars, 0, 55);
    }
}
