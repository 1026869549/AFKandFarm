package com.qiaosylveon.afkfarm;

/**
 * Version hook for the 26.2 target.
 */
public final class VersionBootstrap {

    private VersionBootstrap() {
    }

    public static VersionAdapter create() {
        return new VersionAdapter262();
    }
}
