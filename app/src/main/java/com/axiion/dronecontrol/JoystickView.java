package com.axiion.dronecontrol;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.core.content.ContextCompat;

/**
 * A simple two-axis analog stick.
 *
 * By default both axes self-center (spring back to 0,0) on release, which is
 * right for the roll/pitch stick. For the throttle/yaw stick, set
 * app:autoCenterY="false" (or call setAutoCenterY(false)) so throttle holds
 * its position when you let go, like a real transmitter's throttle ratchet.
 *
 * Reports normalized values in [-1, 1] for both axes, where +Y is UP
 * (screen-down is inverted for you).
 */
public class JoystickView extends View {

    public interface OnStickChangeListener {
        void onChange(float x, float y);
    }

    private final android.graphics.drawable.Drawable baseDrawable;
    private final android.graphics.drawable.Drawable knobDrawable;

    private float centerX, centerY;
    private float maxRadius;
    private float knobRadiusPx;

    private float curX = 0f, curY = 0f; // normalized, -1..1
    private boolean autoCenterX = true;
    private boolean autoCenterY = true;

    private OnStickChangeListener listener;
    private ValueAnimator springAnimator;

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        baseDrawable = ContextCompat.getDrawable(context, R.drawable.shape_joystick_base);
        knobDrawable = ContextCompat.getDrawable(context, R.drawable.shape_joystick_knob);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JoystickView);
            autoCenterX = a.getBoolean(R.styleable.JoystickView_autoCenterX, true);
            autoCenterY = a.getBoolean(R.styleable.JoystickView_autoCenterY, true);
            a.recycle();
        }
    }

    public void setAutoCenterY(boolean value) {
        this.autoCenterY = value;
    }

    public void setOnStickChangeListener(OnStickChangeListener l) {
        this.listener = l;
    }

    /** Externally force the stick (and reported value) back to a given normalized position. */
    public void setPosition(float x, float y) {
        curX = clamp(x);
        curY = clamp(y);
        invalidate();
        notifyListener();
    }

    private float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        knobRadiusPx = Math.min(w, h) * 0.22f;
        maxRadius = Math.min(w, h) / 2f - knobRadiusPx;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        baseDrawable.setBounds(
                (int) (centerX - size / 2f), (int) (centerY - size / 2f),
                (int) (centerX + size / 2f), (int) (centerY + size / 2f));
        baseDrawable.draw(canvas);

        float knobCx = centerX + curX * maxRadius;
        // screen Y grows downward; curY positive means "up" so subtract.
        float knobCy = centerY - curY * maxRadius;

        int kr = (int) knobRadiusPx;
        knobDrawable.setBounds((int) knobCx - kr, (int) knobCy - kr, (int) knobCx + kr, (int) knobCy + kr);
        knobDrawable.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (springAnimator != null) springAnimator.cancel();
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                springBackIfNeeded();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float touchX, float touchY) {
        float dx = touchX - centerX;
        // invert so "up" (smaller screen y) -> positive normalized y
        float dy = -(touchY - centerY);

        float dist = (float) Math.hypot(dx, dy);
        if (dist > maxRadius && dist > 0) {
            float scale = maxRadius / dist;
            dx *= scale;
            dy *= scale;
        }

        curX = maxRadius == 0 ? 0 : dx / maxRadius;
        curY = maxRadius == 0 ? 0 : dy / maxRadius;
        invalidate();
        notifyListener();
    }

    private void springBackIfNeeded() {
        if (!autoCenterX && !autoCenterY) return;

        final float startX = curX;
        final float startY = curY;
        final float endX = autoCenterX ? 0f : curX;
        final float endY = autoCenterY ? 0f : curY;

        springAnimator = ValueAnimator.ofFloat(0f, 1f);
        springAnimator.setDuration(140);
        springAnimator.setInterpolator(new DecelerateInterpolator());
        springAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            curX = startX + (endX - startX) * t;
            curY = startY + (endY - startY) * t;
            invalidate();
            notifyListener();
        });
        springAnimator.start();
    }

    private void notifyListener() {
        if (listener != null) listener.onChange(curX, curY);
    }
}
