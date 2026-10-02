package com.projetos.marvel.ui.room;

import android.graphics.PointF;
import android.graphics.RectF;

import com.projetos.marvel.caseengine.RoomInteractionRange;

/** World-to-screen mapping for a three-room map with a two-axis following camera. */
final class RoomViewport {
    static final float LOGICAL_WIDTH = RoomInteractionRange.LOGICAL_WIDTH;
    static final float LOGICAL_HEIGHT = RoomInteractionRange.LOGICAL_HEIGHT;
    static final float ROOM_WIDTH = 288f;
    static final float ROOM_HEIGHT = 144f;
    static final int ROOM_COUNT = 3;

    private RoomViewport() { }

    static RectF mapRect(float stageWidth, float stageHeight) {
        return mapRect(stageWidth, stageHeight, false);
    }

    /** Keeps the viewport's 2:1 pixels square in either screen orientation. */
    static RectF mapRect(float stageWidth, float stageHeight, boolean rotateForPortrait) {
        if (stageWidth <= 0f || stageHeight <= 0f) return new RectF();
        float scale = Math.min(stageWidth / displayWidth(rotateForPortrait),
                stageHeight / displayHeight(rotateForPortrait));
        float width = displayWidth(rotateForPortrait) * scale;
        float height = displayHeight(rotateForPortrait) * scale;
        float left = (stageWidth - width) / 2f;
        float top = (stageHeight - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }

    static float displayWidth(boolean rotateForPortrait) {
        return rotateForPortrait ? ROOM_HEIGHT : ROOM_WIDTH;
    }

    static float displayHeight(boolean rotateForPortrait) {
        return rotateForPortrait ? ROOM_WIDTH : ROOM_HEIGHT;
    }

    static int roomIndex(float normalizedX) {
        return Math.min(ROOM_COUNT - 1,
                Math.max(0, (int) Math.floor(clamp(normalizedX) * ROOM_COUNT)));
    }

    static float cameraLeft(float normalizedX) {
        float playerX = clamp(normalizedX) * LOGICAL_WIDTH;
        return Math.round(Math.max(0f, Math.min(LOGICAL_WIDTH - ROOM_WIDTH,
                playerX - ROOM_WIDTH / 2f)));
    }

    static float cameraTop(float normalizedY) {
        float playerY = clamp(normalizedY) * LOGICAL_HEIGHT;
        return Math.round(Math.max(0f, Math.min(LOGICAL_HEIGHT - ROOM_HEIGHT,
                playerY - ROOM_HEIGHT / 2f)));
    }

    static float scale(RectF rect, boolean rotateForPortrait) {
        return Math.min(scaleX(rect, rotateForPortrait), scaleY(rect, rotateForPortrait));
    }

    static float scaleX(RectF rect, boolean rotateForPortrait) {
        float width = displayWidth(rotateForPortrait);
        return width <= 0f ? 0f : rect.width() / width;
    }

    static float scaleY(RectF rect, boolean rotateForPortrait) {
        float height = displayHeight(rotateForPortrait);
        return height <= 0f ? 0f : rect.height() / height;
    }

    static PointF displayPoint(float normalizedX, float normalizedY,
                               boolean rotateForPortrait, float cameraLeft, float cameraTop) {
        float localX = normalizedX * LOGICAL_WIDTH - cameraLeft;
        float localY = normalizedY * LOGICAL_HEIGHT - cameraTop;
        if (rotateForPortrait) return new PointF(ROOM_HEIGHT - localY, localX);
        return new PointF(localX, localY);
    }

    static PointF screenPoint(RectF rect, float normalizedX, float normalizedY,
                              boolean rotateForPortrait, float cameraLeft, float cameraTop) {
        PointF point = displayPoint(normalizedX, normalizedY,
                rotateForPortrait, cameraLeft, cameraTop);
        return new PointF(rect.left + point.x * scaleX(rect, rotateForPortrait),
                rect.top + point.y * scaleY(rect, rotateForPortrait));
    }

    static PointF normalizedPoint(RectF rect, float screenX, float screenY,
                                  boolean rotateForPortrait, float cameraLeft, float cameraTop) {
        if (rect.width() <= 0f || rect.height() <= 0f) return new PointF();
        float displayX = clamp((screenX - rect.left) / rect.width());
        float displayY = clamp((screenY - rect.top) / rect.height());
        if (rotateForPortrait) {
            return new PointF((cameraLeft + displayY * ROOM_WIDTH) / LOGICAL_WIDTH,
                    (cameraTop + (1f - displayX) * ROOM_HEIGHT) / LOGICAL_HEIGHT);
        }
        return new PointF((cameraLeft + displayX * ROOM_WIDTH) / LOGICAL_WIDTH,
                (cameraTop + displayY * ROOM_HEIGHT) / LOGICAL_HEIGHT);
    }

    static float screenDepth(float normalizedX, float normalizedY, boolean rotateForPortrait) {
        return rotateForPortrait ? normalizedX : normalizedY;
    }

    static float worldDistanceSquared(float x1, float y1, float x2, float y2) {
        return RoomInteractionRange.distanceSquared(x1, y1, x2, y2);
    }

    static boolean withinInteractionRange(float x1, float y1, float x2, float y2) {
        return RoomInteractionRange.isWithinRange(x1, y1, x2, y2);
    }

    static float interactionRadiusSquared() { return RoomInteractionRange.radiusSquared(); }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
