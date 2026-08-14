package com.w0x7y.justtiers.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.w0x7y.justtiers.JustTiers;

import java.io.IOException;
import java.util.function.Function;

/**
 * Persists an enum as its lower-case {@code id()} — the documented on-disk and
 * command-argument format — while still reading back the upper-case {@code name()} form
 * that earlier builds wrote, since every {@code id()} here is just the lower-cased name.
 *
 * <p>An absent field keeps whatever default the containing object already had; an
 * explicit {@code null} or an unrecognised string both fall back to {@code fallback}
 * with a warning naming the field and the offending value, rather than failing silently.
 */
final class IdEnumAdapter<E extends Enum<E>> extends TypeAdapter<E> {

    private final String field;
    private final E[] values;
    private final E fallback;
    private final Function<E, String> id;

    IdEnumAdapter(String field, Class<E> type, E fallback, Function<E, String> id) {
        this.field = field;
        this.values = type.getEnumConstants();
        this.fallback = fallback;
        this.id = id;
    }

    @Override
    public void write(JsonWriter out, E value) throws IOException {
        out.value(id.apply(value == null ? fallback : value));
    }

    @Override
    public E read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            JustTiers.LOGGER.warn("Config {} was null, using default {}",
                    field, id.apply(fallback));
            return fallback;
        }
        String raw = in.nextString();
        for (E value : values) {
            if (id.apply(value).equalsIgnoreCase(raw)) {
                return value;
            }
        }
        JustTiers.LOGGER.warn("Unrecognised config {} '{}', using default {}",
                field, raw, id.apply(fallback));
        return fallback;
    }
}
