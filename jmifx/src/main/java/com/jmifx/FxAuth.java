package com.jmifx;

/**
 * Memory-only session holder for the OAuth access token. {@link FxHttp#post}
 * attaches it automatically as a Bearer authorization; nothing else reads it.
 * Volatile, static — one logged-in session per client instance (MVP).
 */
public final class FxAuth {

    private static volatile String accessToken;

    private FxAuth() {
    }

    public static String getAccessToken() {
        return accessToken;
    }

    public static void setAccessToken(String token) {
        accessToken = token;
    }

    public static void clear() {
        accessToken = null;
    }

    public static boolean isLoggedIn() {
        return accessToken != null;
    }
}
