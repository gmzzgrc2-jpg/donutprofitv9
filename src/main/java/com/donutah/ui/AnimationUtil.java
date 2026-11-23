package com.donutah.ui;

public final class AnimationUtil {

    private AnimationUtil() {}

    public static float expDecay(float current, float target, float decaySpeed, float deltaSeconds) {
        float clampedDelta = Math.min(deltaSeconds, 0.1f);
        return target + (current - target) * (float) Math.exp(-decaySpeed * clampedDelta);
    }

    public static float easeOutCubic(float t) {
        float clamped = clamp01(t);
        float inv = 1.0f - clamped;
        return 1.0f - (inv * inv * inv);
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static int withAlpha(int rgb, float alpha01) {
        int a = (int) (clamp01(alpha01) * 255.0f) & 0xFF;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int lerpColor(int rgbA, int rgbB, float t) {
        float k = clamp01(t);
        int rA = (rgbA >> 16) & 0xFF;
        int gA = (rgbA >> 8) & 0xFF;
        int bA = rgbA & 0xFF;

        int rB = (rgbB >> 16) & 0xFF;
        int gB = (rgbB >> 8) & 0xFF;
        int bB = rgbB & 0xFF;

        int r = (int) (rA + (rB - rA) * k);
        int g = (int) (gA + (gB - gA) * k);
        int b = (int) (bA + (bB - bA) * k);
        return (r << 16) | (g << 8) | b;
    }

    public static int brighten(int rgb, float factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }
}
