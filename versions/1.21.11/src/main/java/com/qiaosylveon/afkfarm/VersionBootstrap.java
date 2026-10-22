package com.qiaosylveon.afkfarm;

/**
 * Version hook for the 1.21.11 target.
 */
public final class VersionBootstrap {

    private VersionBootstrap() {
    }

    public static VersionAdapter create() {
        return new VersionAdapter12111();
    }
}
