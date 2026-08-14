package com.w0x7y.justtiers.download;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.LongConsumer;

/**
 * Reads a response body as a string while reporting how many bytes have arrived.
 *
 * <p>{@code BodyHandlers.ofString()} exposes no chunk boundaries, so progress cannot be
 * observed through it. This delegates to the same subscriber and counts on the way past.
 */
public final class ProgressBodyHandler implements HttpResponse.BodyHandler<String> {

    private final LongConsumer onBytes;

    public ProgressBodyHandler(LongConsumer onBytes) {
        this.onBytes = onBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
        return new CountingSubscriber(
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), onBytes);
    }

    private static final class CountingSubscriber implements HttpResponse.BodySubscriber<String> {

        private final HttpResponse.BodySubscriber<String> delegate;
        private final LongConsumer onBytes;

        CountingSubscriber(HttpResponse.BodySubscriber<String> delegate, LongConsumer onBytes) {
            this.delegate = delegate;
            this.onBytes = onBytes;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            // Counted before the delegate sees them: it consumes the buffers, so reading
            // remaining() afterwards would report zero.
            long total = 0;
            for (ByteBuffer item : items) {
                total += item.remaining();
            }
            onBytes.accept(total);
            delegate.onNext(items);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public CompletionStage<String> getBody() {
            return delegate.getBody();
        }
    }
}
