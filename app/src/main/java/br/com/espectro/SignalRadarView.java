package br.com.espectro;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Circular strength display: arcs encode signal strength, never geographic angles. */
final class SignalRadarView extends View {
    static final String[] ORDER = {"Wi-Fi", "Bluetooth LE", "Bluetooth clássico", "Wi-Fi Direct", "NFC por aproximação", "Modem celular", "Rede local · mDNS", "USB / OTG"};
    static final int[] COLORS = {0xFFFF4357, 0xFFBE9AFF, 0xFF8E8EFF, 0xFFFF8C77, 0xFFF7C96A, 0xFF81D9CB, 0xFF71BFFA, 0xFFD9A4CE};
    private final Paint paint = new Paint(3);
    private final RectF rect = new RectF();
    private final int[] counts = new int[ORDER.length];
    private final float[] strength = new float[ORDER.length];
    private String total = "0";
    private final Typeface bold = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    SignalRadarView(Context c) { super(c); setContentDescription("Radar de intensidade por origem. Não mostra direção ou posição."); }
    static int color(String type) { for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(type)) return COLORS[i]; return COLORS[0]; }
    void update(Collection<Finding> findings) {
        Arrays.fill(counts, 0); Arrays.fill(strength, 0);
        for (Finding f : findings) for (int i = 0; i < ORDER.length; i++) if (f.methods.contains(ORDER[i])) {
            counts[i]++;
            if (f.rssi != null) strength[i] = Math.max(strength[i], Math.max(.04f, Math.min(1f, (f.rssi + (i == 5 ? 135f : 110f)) / (i == 5 ? 65f : 80f))));
        }
        total = String.valueOf(findings.size()); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density, sp = getResources().getDisplayMetrics().scaledDensity;
        float cx = getWidth() / 2f, cy = 113 * d;
        float outer = Math.min(getWidth() * .39f, 101 * d);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF302A39); paint.setStrokeWidth(d);
        canvas.drawLine(cx - outer - 9 * d, cy, cx + outer + 9 * d, cy, paint);
        canvas.drawLine(cx, cy - outer - 9 * d, cx, cy + outer + 9 * d, paint);
        for (int i = 0; i < ORDER.length; i++) {
            float radius = outer - i * 8.4f * d;
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setColor(0xFF2A2431); paint.setStrokeWidth(4 * d); canvas.drawCircle(cx, cy, radius, paint);
            if (counts[i] > 0) {
                paint.setColor(COLORS[i]); paint.setAlpha(strength[i] > 0 ? 230 : 80);
                // Non-RSSI sources get a small presence marker, not a fabricated strength.
                canvas.drawArc(rect, -90, strength[i] > 0 ? strength[i] * 350 : 8, false, paint);
            }
        }
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(bold);
        paint.setTextSize(25 * sp); paint.setColor(0xFFF2EFF7); canvas.drawText(total, cx, cy + 5 * d, paint);
        paint.setTextSize(7.5f * sp); paint.setColor(0xFFAAA4B6); canvas.drawText("SINAIS", cx, cy + 18 * d, paint);
        paint.setTextSize(10 * sp); canvas.drawText("RADAR DE INTENSIDADE", cx, 242 * d, paint);
        paint.setTextSize(10 * sp); canvas.drawText("Sem posição, distância por arco ou direção", cx, 260 * d, paint);
    }
}
