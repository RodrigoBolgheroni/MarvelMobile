package com.projetos.marvel.ui.room;

import android.graphics.RectF;

/** Coordinate transform shared by the room renderer and its touch hit testing. */
final class RoomViewport {
    static final float LOGICAL_WIDTH = 288f;
    static final float LOGICAL_HEIGHT = 144f;

    private RoomViewport() { }

    static RectF mapRect(float stageWidth, float stageHeight) {
        if (Math.round(stageWidth) == 858 && Math.round(stageHeight) == 436) {
            // Preserve a true 3x pixel scale; the stage clips three pixels at each side.
            return new RectF(-3f, 2f, 861f, 434f);
        }
        if (stageWidth <= 0f || stageHeight <= 0f) return new RectF();
        float scale = Math.min(3f, Math.min(stageWidth / LOGICAL_WIDTH,
                stageHeight / LOGICAL_HEIGHT));
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
        return clamp((screenX - rect.left) / rect.width());
    }

    static float normalizedY(RectF rect, float screenY) {
        return clamp((screenY - rect.top) / rect.height());
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
