package com.projetos.marvel.caseengine;

/** Shared logical room dimensions and interaction range for rendering and case actions. */
public final class RoomInteractionRange {
    public static final float LOGICAL_WIDTH = 288f;
    public static final float LOGICAL_HEIGHT = 144f;
    private static final float RADIUS = 24f;
    private static final float RADIUS_SQUARED = RADIUS * RADIUS;

    private RoomInteractionRange() { }

    public static float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = (x1 - x2) * LOGICAL_WIDTH;
        float dy = (y1 - y2) * LOGICAL_HEIGHT;
        return dx * dx + dy * dy;
    }

    public static boolean isWithinRange(float x1, float y1, float x2, float y2) {
        return distanceSquared(x1, y1, x2, y2) <= RADIUS_SQUARED;
    }

    public static float radiusSquared() { return RADIUS_SQUARED; }
}