package br.com.espectro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Fixed bounds and one cancellable animation: repeated taps never accumulate rotations. */
final class ChevronView extends View {
    private final Paint paint = new Paint(3);
    private final Path path = new Path();
    private float expanded = 1;
    private ValueAnimator animator;
    ChevronView(Context context) {
        super(context);
        paint.setColor(0xFFAAA4B6); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setExpanded(boolean value) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(expanded, value ? 1 : 0);
        animator.setDuration(180);
        animator.addUpdateListener(a -> { expanded = (float)a.getAnimatedValue(); invalidate(); });
        animator.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density, x = getWidth() / 2f, y = getHeight() / 2f;
        float offset = (1 - 2 * expanded) * 3 * d;
        path.reset(); path.moveTo(x - 5 * d, y - offset); path.lineTo(x, y + offset); path.lineTo(x + 5 * d, y - offset);
        canvas.drawPath(path, paint);
    }
    @Override protected void onDetachedFromWindow() { if (animator != null) animator.cancel(); super.onDetachedFromWindow(); }
}
