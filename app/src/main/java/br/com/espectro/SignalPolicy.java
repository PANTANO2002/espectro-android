package br.com.espectro;

/** Experimental log-distance model and conservative absence rules. No Android dependencies. */
final class SignalPolicy {
    static boolean validRssi(int value) { return value >= -127 && value <= -1; }
    static double smooth(Double previous, int sample) {
        return previous == null ? sample : previous * .7 + sample * .3;
    }
    static double meters(double rssi, double referenceAtOneMeter) {
        if (!Double.isFinite(rssi) || !Double.isFinite(referenceAtOneMeter)
                || rssi < -127 || rssi > -1 || referenceAtOneMeter < -100 || referenceAtOneMeter > -10) return Double.NaN;
        // n=2.5 is an assumed environment, not an observed property of the transmitter.
        return Math.max(.1, Math.min(1000, Math.pow(10, (referenceAtOneMeter - rssi) / 25.0)));
    }
    static boolean absent(long ageMs, int successfulMisses, long intervalMs) {
        return ageMs >= Math.max(90000L, intervalMs * 3) && successfulMisses >= 3;
    }
    static boolean monitoring(long elapsedMs, long quietMs) { return elapsedMs >= 45000 && quietMs >= 20000; }
}
