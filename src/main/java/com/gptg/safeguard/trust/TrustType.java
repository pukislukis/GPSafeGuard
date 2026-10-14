package com.gptg.safeguard.trust;

import java.util.HashMap;
import java.util.Map;

/**
 * Merepresentasikan 4 level trust bawaan GriefPrevention:
 * /accesstrust, /containertrust, /trust (full/build), /permissiontrust
 */
public enum TrustType {
    ACCESS("Access"),
    CONTAINER("Container"),
    BUILD("Build"),
    PERMISSION("Permission");

    private final String displayName;

    TrustType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    private static final Map<String, TrustType> BY_LABEL = new HashMap<>();

    /**
     * Daftarkan alias command (dipanggil dari ConfigManager berdasarkan config.yml
     * trust.intercepted-commands) supaya listener bisa mem-parsing label command
     * menjadi TrustType yang tepat.
     */
    public static void registerLabel(String label, TrustType type) {
        BY_LABEL.put(label.toLowerCase(), type);
    }

    public static TrustType fromLabel(String label) {
        return BY_LABEL.get(label.toLowerCase());
    }

    public static void resetRegistry() {
        BY_LABEL.clear();
    }
}
