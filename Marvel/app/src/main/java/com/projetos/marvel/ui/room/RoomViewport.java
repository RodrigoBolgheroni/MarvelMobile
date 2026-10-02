package com.projetos.marvel.ui.room;

import android.graphics.PointF;
import android.graphics.RectF;

import com.projetos.marvel.caseengine.RoomInteractionRange;

/** Shared logical-room transforms and interaction metric for rendering and touch. */
final class RoomViewport {
    static final float LOGICAL_WIDTH = RoomInteractionRange.LOGICAL_WIDTH;
    static final float LOGICAL_HEIGHT = RoomInteractionRange.LOGICAL_HEIGHT;
    static final float ROOM_WIDTH = 288f;
    static final float ROOM_HEIGHT = LOGICAL_HEIGHT;
    static final int ROOM_COUNT = 3;

    private RoomViewport() { }

    /** Full-stage transform keeps the room edge-to-edge in either orientation. */
    static RectF mapRect(float stageWidth, float stageHeight) {
        return mapRect(stageWidth, stageHeight, false);
    }

    /** Fits one room inside the canvas while preserving the native pixel-art aspect ratio. */
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
        float left = Math.max(0f, Math.min(LOGICAL_WIDTH - ROOM_WIDTH,
                playerX - ROOM_WIDTH / 2f));
        return Math.round(left);
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

    /** World-normalized anchor mapped into display-plane logical pixels. */
    static PointF displayPoint(float normalizedX, float normalizedY,
                               boolean rotateForPortrait, float cameraLeft) {
        float localX = normalizedX * LOGICAL_WIDTH - cameraLeft;
        float localY = normalizedY * LOGICAL_HEIGHT;
        if (rotateForPortrait) {
            return new PointF(ROOM_HEIGHT - localY, localX);
        }
        return new PointF(localX, localY);
    }

    /** Converts a world-normalized anchor into physical screen pixels. */
    static PointF screenPoint(RectF rect, float normalizedX, float normalizedY,
                              boolean rotateForPortrait, float cameraLeft) {
        PointF displayPoint = displayPoint(normalizedX, normalizedY,
                rotateForPortrait, cameraLeft);
        return new PointF(
                rect.left + displayPoint.x * scaleX(rect, rotateForPortrait),
                rect.top + displayPoint.y * scaleY(rect, rotateForPortrait));
    }

    /** Inverse screen mapping used before floor taps enter the unchanged logical walkmap. */
    static PointF normalizedPoint(RectF rect, float screenX, float screenY,
                                  boolean rotateForPortrait, float cameraLeft) {
        if (rect.width() <= 0f || rect.height() <= 0f) return new PointF();
        float displayX = clamp((screenX - rect.left) / rect.width());
        float displayY = clamp((screenY - rect.top) / rect.height());
        if (rotateForPortrait) {
            return new PointF((cameraLeft + displayY * ROOM_WIDTH)
                    / LOGICAL_WIDTH, 1f - displayX);
        }
        return new PointF((cameraLeft + displayX * ROOM_WIDTH)
                / LOGICAL_WIDTH, displayY);
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
