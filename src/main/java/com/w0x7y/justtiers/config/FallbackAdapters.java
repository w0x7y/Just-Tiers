package com.w0x7y.justtiers.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.w0x7y.justtiers.JustTiers;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Lets a hand-edited config lose only the values it got wrong. {@link #perValue} reads
 * each value on its own, and the strict adapters refuse what Gson would otherwise coerce.
 */
final class FallbackAdapters {

    /** JSON booleans and the strings true and false; Gson alone reads "maybe" as false. */
    static final TypeAdapter<Boolean> BOOLEAN = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Boolean value) throws IOException {
            out.value(value);
        }

        @Override
        public Boolean read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.STRING) {
                return in.nextBoolean();
            }
            String raw = in.nextString();
            if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                throw new JsonSyntaxException("Expected a boolean but was '" + raw + "'");
            }
            return Boolean.parseBoolean(raw);
        }
    };

    /** Gson alone wraps an overflowing number, so 1e12 minutes would clamp to the minimum. */
    static final TypeAdapter<Integer> INTEGER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Integer value) throws IOException {
            out.value(value);
        }

        @Override
        public Integer read(JsonReader in) throws IOException {
            // The cast saturates at the int range, which the setters then clamp.
            return (int) new BigDecimal(in.nextString()).doubleValue();
        }
    };

    /**
     * Reads every value except {@code root} itself on its own. A refused value reads as
     * null: a primitive field then keeps its default, the getters read a null object as
     * their default, and a null map entry falls back for that key alone.
     */
    static TypeAdapterFactory perValue(Class<?> root) {
        return new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType() == root) {
                    return null;
                }
                TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        delegate.write(out, value);
                    }

                    @Override
                    public T read(JsonReader in) {
                        String path = in.getPath();
                        JsonElement json = JsonParser.parseReader(in);
                        try {
                            return delegate.fromJsonTree(json);
                        } catch (RuntimeException e) {
                            JustTiers.LOGGER.warn("Invalid config value {} at {}, using default",
                                    json, path);
                            return null;
                        }
                    }
                };
            }
        };
    }

    private FallbackAdapters() {
    }
}
