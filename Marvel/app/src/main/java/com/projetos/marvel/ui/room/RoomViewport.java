package com.projetos.marvel.ui.room;

import android.graphics.PointF;
import android.graphics.RectF;

import com.projetos.marvel.caseengine.RoomInteractionRange;

/** Shared logical-room transforms and interaction metric for rendering and touch. */
final class RoomViewport {
    static final float LOGICAL_WIDTH = RoomInteractionRange.LOGICAL_WIDTH;
    static final float LOGICAL_HEIGHT = RoomInteractionRange.LOGICAL_HEIGHT;

    private RoomViewport() { }

    /** Uniform contain transform: preserve all room edges and use nearest-neighbor sampling. */
    static RectF mapRect(float stageWidth, float stageHeight) {
        return mapRect(stageWidth, stageHeight, false);
    }

    /**
     * Computes the centered viewport for either the normal landscape room or its portrait turn.
     * Portrait rendering uses a 144 by 288 display plane while world coordinates stay 288 by 144.
     */
    static RectF mapRect(float stageWidth, float stageHeight, boolean rotateForPortrait) {
        if (stageWidth <= 0f || stageHeight <= 0f) return new RectF();
        float displayWidth = displayWidth(rotateForPortrait);
        float displayHeight = displayHeight(rotateForPortrait);
        float scale = Math.min(stageWidth / displayWidth, stageHeight / displayHeight);
        float width = displayWidth * scale;
        float height = displayHeight * scale;
        float left = (stageWidth - width) / 2f;
        float top = (stageHeight - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }

    static float displayWidth(boolean rotateForPortrait) {
        return rotateForPortrait ? LOGICAL_HEIGHT : LOGICAL_WIDTH;
    }

    static float displayHeight(boolean rotateForPortrait) {
        return rotateForPortrait ? LOGICAL_WIDTH : LOGICAL_HEIGHT;
    }

    static float scale(RectF rect, boolean rotateForPortrait) {
        float width = displayWidth(rotateForPortrait);
        return width <= 0f ? 0f : rect.width() / width;
    }

    /** World-normalized anchor mapped into display-plane logical pixels. */
    static PointF displayPoint(float normalizedX, float normalizedY, boolean rotateForPortrait) {
        if (rotateForPortrait) {
            return new PointF(LOGICAL_HEIGHT * (1f - normalizedY),
                    LOGICAL_WIDTH * normalizedX);
        }
        return new PointF(LOGICAL_WIDTH * normalizedX, LOGICAL_HEIGHT * normalizedY);
    }

    /** Converts a world-normalized anchor into physical screen pixels. */
    static PointF screenPoint(RectF rect, float normalizedX, float normalizedY,
                              boolean rotateForPortrait) {
        PointF displayPoint = displayPoint(normalizedX, normalizedY, rotateForPortrait);
        float scale = scale(rect, rotateForPortrait);
        return new PointF(rect.left + displayPoint.x * scale,
                rect.top + displayPoint.y * scale);
    }

    /** Inverse screen mapping used before floor taps enter the unchanged logical walkmap. */
    static PointF normalizedPoint(RectF rect, float screenX, float screenY,
                                  boolean rotateForPortrait) {
        if (rect.width() <= 0f || rect.height() <= 0f) return new PointF();
        float displayX = clamp((screenX - rect.left) / rect.width());
        float displayY = clamp((screenY - rect.top) / rect.height());
        if (rotateForPortrait) {
            return new PointF(displayY, 1f - displayX);
        }
        return new PointF(displayX, displayY);
    }

    /** Logical coordinate that determines front-to-back order in the displayed orientation. */
    static float screenDepth(float normalizedX, float normalizedY, boolean rotateForPortrait) {
        return rotateForPortrait ? normalizedX : normalizedY;
    }

    static float screenX(RectF rect, float normalizedX) {
        return rect.left + rect.width() * normalizedX;
    }

    static float screenY(RectF rect, float normalizedY) {
        return rect.top + rect.height() * normalizedY;
    }

    static float normalizedX(RectF rect, float screenX) {
        return rect.width() <= 0f ? 0f : clamp((screenX - rect.left) / rect.width());
    }

    static float normalizedY(RectF rect, float screenY) {
        return rect.height() <= 0f ? 0f : clamp((screenY - rect.top) / rect.height());
    }

    static float worldDistanceSquared(float x1, float y1, float x2, float y2) {
        return RoomInteractionRange.distanceSquared(x1, y1, x2, y2);
    }

    static boolean withinInteractionRange(float x1, float y1, float x2, float y2) {
        return RoomInteractionRange.isWithinRange(x1, y1, x2, y2);
    }

    static float interactionRadiusSquared() { return RoomInteractionRange.radiusSquared(); }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
