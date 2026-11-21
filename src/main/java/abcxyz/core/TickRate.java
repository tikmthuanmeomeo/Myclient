package abcxyz.core;

/**
 * Server TPS guessed from the time between two world time packets (the server sends one every 20 ticks).
 * Fed by ClientPlayNetworkHandlerMixin.
 */
public final class TickRate {
    private static final double[] samples = new double[4];
    private static int count = 0;
    private static int index = 0;
    private static long last = 0L;

    private TickRate() {
    }

    public static void onTimeUpdate() {
        long now = System.nanoTime();
        if (last != 0L) {
            double seconds = (now - last) / 1.0e9;
            if (seconds > 0.2 && seconds < 10.0) {
                samples[index] = Math.min(20.0, 20.0 / seconds);
                index = (index + 1) % samples.length;
                count = Math.min(count + 1, samples.length);
            } else {
                count = 0;
            }
        }
        last = now;
    }

    /** Average TPS (0 to 20), or -1 when there is no fresh data yet. */
    public static float tps() {
        if (count == 0 || System.nanoTime() - last > 6_000_000_000L) return -1f;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return (float) (sum / count);
    }
}
