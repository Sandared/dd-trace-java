package com.datadoghq.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.Test;

import com.datadoghq.trace.DDSpan;
import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTracer;


public class DDSpanTest {


    @Test
    public void testGetterSetter() {

        DDSpanContext context = new DDSpanContext(
                1L,
                1L,
                0L,
                "fakeService",
                "fakeOperation",
                "fakeResource",
                Collections.<String, String>emptyMap(),
                false,
                "fakeType",
                null,
                null,
                null);


        String expected;
        DDSpan span = new DDSpan(1L, context);

        expected = "service";
        span.setServiceName(expected);
        assertThat(span.getServiceName()).isEqualTo(expected);

        expected = "operation";
        span.setOperationName(expected);
        assertThat(span.getOperationName()).isEqualTo(expected);

        expected = "resource";
        span.setResourceName(expected);
        assertThat(span.getResourceName()).isEqualTo(expected);

        expected = "type";
        span.setSpanType(expected);
        assertThat(span.getType()).isEqualTo(expected);

    }


    @Test
    public void shouldResourceNameEqualsOperationNameIfNull() {

        final String expectedName = "operationName";

        DDSpan span = new DDTracer().buildSpan(expectedName).withServiceName("foo").start();
        // ResourceName = expectedName
        assertThat(span.getResourceName()).isEqualTo(expectedName);

        // ResourceName = expectedResourceName
        final String expectedResourceName = "fake";
        span = new DDTracer()
                .buildSpan(expectedName)
                .withResourceName(expectedResourceName)
                .withServiceName("foo").start();

        assertThat(span.getResourceName()).isEqualTo(expectedResourceName);

    }


}