package com.floye.openpacrestrict;

public final class ClaimLogGate {
    private static volatile boolean enabled = false; // bloqué par défaut
    private static int ticksRemaining = 0;

    private ClaimLogGate() {}

    public static void start(int seconds) {
        ticksRemaining = Math.max(0, seconds * 20);
        enabled = ticksRemaining == 0;
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                enabled = true;
                OpenpacRestrict.LOGGER.info("[OpenpacRestrict] Claim logs enabled after startup delay.");
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void reset() {
        enabled = false;
        ticksRemaining = 0;
    }
}