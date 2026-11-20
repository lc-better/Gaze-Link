package com.demo.opencv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Directional gaze hint; it is intentionally not presented as a precise 2D cursor. */
public final class GazeFocusOverlay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int direction;
    private boolean visible, locked;
    private float progress;
    private float x, targetX;
    private View leftTarget, centreTarget, rightTarget;
    private final int[] overlayLocation = new int[2], targetLocation = new int[2];

    public GazeFocusOverlay(Context context) { super(context); setClickable(false); setWillNotDraw(false); }
    public void setTargets(View left, View centre, View right) {
        leftTarget = left; centreTarget = centre; rightTarget = right;
    }
    public void setState(int direction, boolean valid, float dwellProgress, boolean armed) {
        this.visible = valid && direction >= 0 && direction <= 2;
        this.direction = direction; this.progress = Math.max(0, Math.min(1, dwellProgress));
        this.locked = progress > 0 || (armed && direction == 0);
        View target = direction == 1 ? leftTarget : direction == 2 ? rightTarget : centreTarget;
        if (target != null && target.getWidth() > 0 && getWidth() > 0) {
            getLocationInWindow(overlayLocation); target.getLocationInWindow(targetLocation);
            targetX = (targetLocation[0] - overlayLocation[0] + target.getWidth() / 2f) / getWidth();
        } else targetX = direction == 1 ? .125f : direction == 2 ? .875f : .5f;
        if (x == 0) x = targetX;
        else x += (targetX - x) * .25f;
        invalidate();
    }
    public void hide() { visible = false; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible || getWidth() == 0 || getHeight() == 0) return;
        float cy = getHeight() * .5f;
        if (centreTarget != null && centreTarget.getHeight() > 0) {
            getLocationInWindow(overlayLocation); centreTarget.getLocationInWindow(targetLocation);
            cy = targetLocation[1] - overlayLocation[1] + centreTarget.getHeight() / 2f;
        }
        float cx = x * getWidth(), radius = 22 * getResources().getDisplayMetrics().density;
        int color = locked ? 0xff0b7285 : 0xff526579;
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setAlpha(locked ? 48 : 28);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(locked ? 3 : 2);
        paint.setColor(locked ? 0xff0b7285 : 0xff526579); paint.setAlpha(locked ? 220 : 120);
        canvas.drawCircle(cx, cy, radius, paint);
        if (locked && progress > 0) {
            paint.setColor(0xff2f9e44); paint.setStrokeWidth(4); paint.setStrokeCap(Paint.Cap.ROUND);
            arc.set(cx - radius - 5, cy - radius - 5, cx + radius + 5, cy + radius + 5);
            canvas.drawArc(arc, -90, progress * 360, false, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
    }
}
