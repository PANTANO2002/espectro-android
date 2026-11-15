package br.com.espectro;

public final class SignalPolicyTest {
    private static int checks;
    private static void expect(boolean value, String description) { checks++; if (!value) throw new AssertionError(description); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < .00001; }
    public static void main(String[] args) {
        expect(near(SignalPolicy.meters(-59, -59), 1), "calibration at 1 m");
        expect(near(SignalPolicy.meters(-84, -59), 10), "25 dB loss gives tenfold distance for n=2.5");
        expect(near(SignalPolicy.meters(-34, -59), .1), "close model boundary");
        for (int rssi = -126; rssi < -20; rssi++) expect(SignalPolicy.meters(rssi, -59) >= SignalPolicy.meters(rssi + 1, -59), "weaker signal never closer: " + rssi);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, 127, -128}) expect(Double.isNaN(SignalPolicy.meters(invalid, -59)), "invalid signal rejected");
        expect(Double.isNaN(SignalPolicy.meters(-60, Double.NaN)), "invalid calibration rejected");
        expect(!SignalPolicy.validRssi(0) && !SignalPolicy.validRssi(127) && SignalPolicy.validRssi(-80), "Android unavailable RSSI values rejected");
        expect(near(SignalPolicy.smooth(null, -60), -60), "first sample unchanged");
        double filtered = SignalPolicy.smooth(-60.0, -90);
        expect(filtered < -60 && filtered > -90, "sudden jump smoothed");
        for (int i = 0; i < 50; i++) filtered = SignalPolicy.smooth(filtered, -90);
        expect(Math.abs(filtered + 90) < .001, "stable samples converge");
        expect(!SignalPolicy.absent(-1, 20, 60000), "unknown cache age never timed out as measured presence");
        expect(!SignalPolicy.absent(600000, 0, 60000), "time alone cannot remove a radio result");
        expect(!SignalPolicy.absent(600000, 2, 60000), "one or two misses preserve result");
        expect(!SignalPolicy.absent(179999, 3, 60000), "three refresh intervals grace");
        expect(SignalPolicy.absent(180000, 3, 60000), "old result removed after confirmed misses");
        expect(!SignalPolicy.absent(89999, 5, 10000), "minimum 90 s grace");
        expect(SignalPolicy.absent(90000, 3, 10000), "minimum grace boundary");
        expect(!SignalPolicy.absent(180000, 3, 300000), "long refresh interval respected");
        expect(!SignalPolicy.monitoring(44000, 25000), "initial full scan lasts at least 45 s");
        expect(!SignalPolicy.monitoring(90000, 19999), "new finding extends discovery phase");
        expect(SignalPolicy.monitoring(45000, 20000), "quiet environment enters monitoring");
        System.out.println(checks + " signal-policy checks passed");
    }
}
