package dev.codearena.app.config;

import dev.codearena.app.exception.MissingUserHeaderException;
import org.bson.types.ObjectId;

public final class UserHeader {

    public static final String NAME = "X-User-Id";

    private UserHeader() {}

    public static ObjectId requireUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new MissingUserHeaderException("Missing X-User-Id header — gateway should set this");
        }
        try {
            return new ObjectId(header);
        } catch (IllegalArgumentException e) {
            throw new MissingUserHeaderException("X-User-Id is not a valid ObjectId");
        }
    }
}
