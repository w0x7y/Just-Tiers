package com.w0x7y.justtiers.api;

/** A lookup that could not be completed, as opposed to a player who is simply unranked. */
public class TierLookupException extends RuntimeException {

    public TierLookupException(String message) {
        super(message);
    }
}
