package com.w0x7y.justtiers.download;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBodyHandlerTest {

    private final AtomicLong counted = new AtomicLong();

    /** The subscriber only needs a subscription that does not explode when requested from. */
    private static final Flow.Subscription NO_OP_SUBSCRIPTION = new Flow.Subscription() {
        @Override public void request(long n) { }
        @Override public void cancel() { }
    };

    private static final HttpResponse.ResponseInfo RESPONSE_INFO = new HttpResponse.ResponseInfo() {
        @Override public int statusCode() { return 200; }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    };

    private HttpResponse.BodySubscriber<String> subscriber() {
        HttpResponse.BodySubscriber<String> subscriber =
                new ProgressBodyHandler(counted::addAndGet).apply(RESPONSE_INFO);
        subscriber.onSubscribe(NO_OP_SUBSCRIPTION);
        return subscriber;
    }

    private static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void countsBytesAndStillDeliversTheBody() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("hello")));
        subscriber.onComplete();

        assertEquals("hello", subscriber.getBody().toCompletableFuture().get());
        assertEquals(5, counted.get());
    }

    @Test
    void sumsAcrossChunks() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("abc"), bytes("de")));
        subscriber.onNext(List.of(bytes("fgh")));
        subscriber.onComplete();

        assertEquals("abcdefgh", subscriber.getBody().toCompletableFuture().get());
        assertEquals(8, counted.get());
    }

    @Test
    void countsNothingForAnEmptyBody() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onComplete();

        assertEquals("", subscriber.getBody().toCompletableFuture().get());
        assertEquals(0, counted.get());
    }

    @Test
    void propagatesErrors() {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("partial")));
        subscriber.onError(new java.io.IOException("connection reset"));

        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
