package com.backtester;

/** Single source for the version shown by all desktop front ends. */
public final class ApplicationVersion {

    private static final String DEVELOPMENT_FALLBACK = "1.2.6";

    private ApplicationVersion() {
    }

    public static String current() {
        Package appPackage = ApplicationVersion.class.getPackage();
        String implementationVersion = appPackage != null
                ? appPackage.getImplementationVersion() : null;
        return implementationVersion == null || implementationVersion.isBlank()
                ? DEVELOPMENT_FALLBACK : implementationVersion;
    }

    public static String display() {
        return "v" + current();
    }
}
