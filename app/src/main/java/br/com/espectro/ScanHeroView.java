package br.com.espectro;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Symbolic animated antenna. No dots, bearings, or fabricated device positions. */
final class ScanHeroView extends View {
    private final Paint paint = new Paint(3);
    private final Path path = new Path();
    private final RectF arc = new RectF();
    private final RectF button = new RectF();
    private final Typeface labelTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
    private final Typeface captionTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
    private RadialGradient glow;
    private boolean scanning;
    private boolean animating = true;
    private int seconds;
    private String subtitle = "Toque para explorar os sinais ao redor";
    private final int red = Color.rgb(255, 67, 87);
    private long began = SystemClock.uptimeMillis();

    ScanHeroView(Context context) {
        super(context);
        setContentDescription("Escanear aparelhos próximos. Antena animada decorativa.");
        setClickable(true);
        setFocusable(true);
    }

    void update(boolean active, int remaining) {
        scanning = active; seconds = remaining;
        subtitle = active ? remaining + " s restantes · observando sinais" : "Toque para explorar os sinais ao redor";
        setContentDescription(active ? "Parar varredura. " + remaining + " segundos restantes" : "Escanear aparelhos próximos");
        invalidate();
    }

    void animateWhileVisible(boolean value) { animating = value; invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float d = getResources().getDisplayMetrics().density;
        float r = Math.min(h * .36f, 108 * d);
        if (r > 0) glow = new RadialGradient(w / 2f, h * .46f, r * 1.6f, new int[]{0x38FF4357, 0x04FF4357, 0x00FF4357}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() * .46f;
        float radius = Math.min(getHeight() * .36f, 108 * d);
        float time = (SystemClock.uptimeMillis() - began) / (scanning ? 1700f : 3300f);
        float pulse = (float)(.5 + .5 * Math.sin(time * Math.PI * 2));
        paint.setShader(glow);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius * 1.6f, paint);
        paint.setShader(null);

        for (int i = 0; i < 3; i++) {
            float phase = (time + i / 3f) % 1f;
            float r = radius * (.62f + phase * .75f);
            paint.setColor(red);
            paint.setAlpha((int)((1 - phase) * (scanning ? 115 : 65)));
            paint.setStrokeWidth(d);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, r, paint);
        }
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF5C2834);
        paint.setStrokeWidth(1 * d);
        canvas.drawLine(cx - radius * 1.16f, cy, cx + radius * 1.16f, cy, paint);
        canvas.drawLine(cx, cy - radius * 1.16f, cx, cy + radius * 1.16f, paint);

        // Tower is behind the central label, with two broadcast lobes above it.
        float tipY = cy - 44 * d;
        paint.setColor(red); paint.setAlpha((int)(155 + pulse * 80));
        paint.setStrokeWidth(2.3f * d);
        path.reset();
        path.moveTo(cx, tipY); path.lineTo(cx - 29 * d, cy + 57 * d);
        path.moveTo(cx, tipY); path.lineTo(cx + 29 * d, cy + 57 * d);
        path.moveTo(cx - 16 * d, cy + 13 * d); path.lineTo(cx + 21 * d, cy + 32 * d);
        path.moveTo(cx + 16 * d, cy + 13 * d); path.lineTo(cx - 21 * d, cy + 32 * d);
        canvas.drawPath(path, paint);
        for (int i = 1; i <= 2; i++) {
            float r = i * 16 * d;
            arc.set(cx - r, tipY - r, cx + r, tipY + r);
            canvas.drawArc(arc, 142, 77, false, paint);
            canvas.drawArc(arc, -39, 77, false, paint);
        }
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
        canvas.drawCircle(cx, tipY, 4 * d, paint);

        float width = 200 * d;
        button.set(cx - width / 2, cy - 25 * d, cx + width / 2, cy + 25 * d);
        paint.setColor(red);
        canvas.drawRoundRect(button, 15 * d, 15 * d, paint);
        paint.setTypeface(labelTypeface);
        paint.setTextSize(17 * getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        String label = scanning ? "PARAR VARREDURA" : "ESCANEAR";
        canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
        paint.setTypeface(captionTypeface);
        paint.setColor(0xFFA7A1B2);
        paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText(subtitle, cx, Math.min(getHeight() - 14 * d, cy + radius + 26 * d), paint);
        if (animating && android.animation.ValueAnimator.areAnimatorsEnabled() && isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
}
