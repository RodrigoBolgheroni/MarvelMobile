package com.projetos.marvel.ui.room;

import android.graphics.RectF;

import com.projetos.marvel.caseengine.RoomInteractionRange;

/** Shared logical-room transforms and interaction metric for rendering and touch. */
final class RoomViewport {
    static final float LOGICAL_WIDTH = RoomInteractionRange.LOGICAL_WIDTH;
    static final float LOGICAL_HEIGHT = RoomInteractionRange.LOGICAL_HEIGHT;

    private RoomViewport() { }

    /** Uniform contain transform: preserve all room edges and use nearest-neighbor sampling. */
    static RectF mapRect(float stageWidth, float stageHeight) {
        if (stageWidth <= 0f || stageHeight <= 0f) return new RectF();
        float scale = Math.min(stageWidth / LOGICAL_WIDTH, stageHeight / LOGICAL_HEIGHT);
        float width = LOGICAL_WIDTH * scale;
        float height = LOGICAL_HEIGHT * scale;
        float left = (stageWidth - width) / 2f;
        float top = (stageHeight - height) / 2f;
        return new RectF(left, top, left + width, top + height);
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
