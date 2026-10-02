package com.projetos.marvel.ui.room;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.projetos.marvel.R;
import com.projetos.marvel.caseengine.CharacterView;
import com.projetos.marvel.caseengine.ClueView;
import com.projetos.marvel.caseengine.RoomViewState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pixel-art room renderer and touch surface. Entity anchors always come from RoomViewState. */
public final class RoomCanvasView extends View {
    public interface Listener {
        void onFloorTapped(float x, float y);
        void onCharacterTapped(CharacterView character);
        void onClueTapped(ClueView clue);
    }

    public interface MovementListener {
        void onArrived(float normalizedX, float normalizedY);
    }

    private static final int INK = 0xFF0D1720;
    private static final int PANEL = 0xFF243543;
    private static final int FLOOR = 0xFF384B57;
    private static final int DECOR = 0xFF52666A;
    private static final int IVORY = 0xFFC4D5C8;
    private static final int MINT = 0xFF44CFBD;
    private static final int AMBER = 0xFFF2B84B;
    private static final int CORAL = 0xFFE4675D;

    private final Paint bitmapPaint = new Paint();
    private final Paint shapePaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint textPaint = new Paint();
    private final RectF mapRect = new RectF();
    private final float density;
    private final float scaledDensity;

    private Bitmap roomMap;
    private Bitmap roomTiles;
    private Bitmap roomProps;
    private Bitmap briefingTable;
    private Bitmap clueAtlas;
    private Bitmap playerSheet;
    private Bitmap survivorAtlas;
    private RoomWalkMap walkMap;
    private RoomViewState state;
    private Listener listener;
    private ValueAnimator movementAnimator;
    private float animatedPlayerX = Float.NaN;
    private float animatedPlayerY = Float.NaN;
    private boolean portraitRotation;
    private int facingRow;
    private int movementFrame;
    private long movementStartMs;

    public RoomCanvasView(Context context) { this(context, null); }

    public RoomCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        bitmapPaint.setFilterBitmap(false);
        bitmapPaint.setDither(false);
        shapePaint.setAntiAlias(false);
        linePaint.setAntiAlias(false);
        textPaint.setAntiAlias(false);
        textPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12f * scaledDensity);
        roomMap = loadDrawableBitmap("room_map_864x144");
        roomTiles = loadDrawableBitmap("room_tiles_16");
        roomProps = loadDrawableBitmap("room_props_16");
        briefingTable = loadDrawableBitmap("room_tactical_briefing_table");
        clueAtlas = loadDrawableBitmap("room_clue_atlas_48x16");
        playerSheet = loadDrawableBitmap("player_sheet_64x96");
        survivorAtlas = loadDrawableBitmap("survivor_idle_atlas_80x24");
        setClickable(true);
        setFocusable(true);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setWalkMap(RoomWalkMap walkMap) {
        this.walkMap = walkMap;
        invalidate();
    }

    public void render(RoomViewState state) {
        this.state = state;
        if (movementAnimator == null) resetAnimatedPlayerToState();
        invalidate();
    }

    public void cancelMovement() {
        ValueAnimator animator = movementAnimator;
        movementAnimator = null;
        if (animator != null) animator.cancel();
        resetAnimatedPlayerToState();
        invalidate();
    }

    public PointF getCurrentPlayerPosition() {
        if (state == null) return new PointF(0.5f, 0.9f);
        float x = Float.isNaN(animatedPlayerX) ? state.getPlayer().getX() : animatedPlayerX;
        float y = Float.isNaN(animatedPlayerY) ? state.getPlayer().getY() : animatedPlayerY;
        return new PointF(x, y);
    }

    public void animatePlayerAlong(List<PointF> normalizedPath, MovementListener callback) {
        if (normalizedPath == null || normalizedPath.isEmpty() || state == null) return;
        cancelMovement();
        final List<PointF> path = new ArrayList<>(normalizedPath);
        if (path.size() == 1) path.add(new PointF(path.get(0).x, path.get(0).y));
        final float[] segmentLengths = new float[path.size() - 1];
        float totalLength = 0f;
        for (int i = 0; i < segmentLengths.length; i++) {
            PointF from = path.get(i);
            PointF to = path.get(i + 1);
            float dx = (to.x - from.x) * RoomViewport.LOGICAL_WIDTH;
            float dy = (to.y - from.y) * RoomViewport.LOGICAL_HEIGHT;
            segmentLengths[i] = (float) Math.hypot(dx, dy);
            totalLength += segmentLengths[i];
        }
        if (totalLength <= 0.001f) {
            PointF end = path.get(path.size() - 1);
            animatedPlayerX = end.x;
            animatedPlayerY = end.y;
            if (callback != null) callback.onArrived(end.x, end.y);
            invalidate();
            return;
        }

        movementStartMs = SystemClock.uptimeMillis();
        updateFacing(path.get(0), path.get(1));
        ValueAnimator animator = ValueAnimator.ofFloat(0f, totalLength);
        movementAnimator = animator;
        animator.setDuration(Math.max(180L, Math.round(totalLength * 18f)));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            if (movementAnimator != animator) return;
            updateMovementPosition(path, segmentLengths, (float) value.getAnimatedValue());
            movementFrame = 1 + (int) ((SystemClock.uptimeMillis() - movementStartMs) / 125L % 3L);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator finished) {
                if (movementAnimator != animator) return;
                movementAnimator = null;
                movementFrame = 0;
                PointF end = path.get(path.size() - 1);
                animatedPlayerX = end.x;
                animatedPlayerY = end.y;
                invalidate();
                if (callback != null) callback.onArrived(end.x, end.y);
            }
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(INK);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        computeMapRect();
        float cameraLeft = cameraLeft();

        int clipSave = canvas.save();
        canvas.clipRect(0f, 0f, getWidth(), getHeight());

        int baseSave = canvas.save();
        canvas.translate(mapRect.left, mapRect.top);
        canvas.scale(RoomViewport.scaleX(mapRect, portraitRotation),
                RoomViewport.scaleY(mapRect, portraitRotation));
        if (portraitRotation) {
            // Clockwise map transform: logical (x, y) is displayed at (height - y, x).
            canvas.translate(RoomViewport.LOGICAL_HEIGHT, 0f);
            canvas.rotate(90f);
        }

        boolean modularBase = drawRoomBase(canvas, cameraLeft);
        if (modularBase) {
            canvas.save();
            canvas.translate(-cameraLeft, 0f);
            drawProps(canvas);
            canvas.restore();
        }
        canvas.restoreToCount(baseSave);

        if (state != null) {
            int entitySave = canvas.save();
            canvas.translate(mapRect.left, mapRect.top);
            canvas.scale(RoomViewport.scaleX(mapRect, portraitRotation),
                    RoomViewport.scaleY(mapRect, portraitRotation));
            drawDynamicEntities(canvas, portraitRotation, cameraLeft);
            drawNearbyAction(canvas, portraitRotation);
            canvas.restoreToCount(entitySave);
        }
        canvas.restoreToCount(clipSave);
    }

    private void drawDynamicEntities(Canvas canvas, boolean rotateForPortrait, float cameraLeft) {
        List<RenderEntity> entities = new ArrayList<>();
        for (ClueView clue : state.getClues()) {
            PointF point = RoomViewport.displayPoint(
                    clue.getX(), clue.getY(), rotateForPortrait, cameraLeft);
            entities.add(RenderEntity.forClue(clue,
                    point.y));
        }
        for (CharacterView character : state.getCharacters()) {
            PointF point = RoomViewport.displayPoint(
                    character.getX(), character.getY(), rotateForPortrait, cameraLeft);
            entities.add(RenderEntity.forCharacter(character,
                    point.y));
        }
        float playerY = Float.isNaN(animatedPlayerY)
                ? state.getPlayer().getY() : animatedPlayerY;
        float playerX = Float.isNaN(animatedPlayerX)
                ? state.getPlayer().getX() : animatedPlayerX;
        entities.add(RenderEntity.forPlayer(RoomViewport.displayPoint(
                playerX, playerY, rotateForPortrait, cameraLeft).y));
        Collections.sort(entities, (first, second) -> Float.compare(first.footY, second.footY));

        for (RenderEntity entity : entities) {
            if (entity.clue != null) drawClue(canvas, entity.clue, rotateForPortrait, cameraLeft);
            else if (entity.character != null) {
                drawCharacter(canvas, entity.character, rotateForPortrait, cameraLeft);
            } else drawPlayer(canvas, rotateForPortrait, cameraLeft);
        }
    }

    private boolean drawRoomBase(Canvas canvas, float cameraLeft) {
        RectF logicalBounds = new RectF(0f, 0f,
                RoomViewport.ROOM_WIDTH, RoomViewport.ROOM_HEIGHT);
        if (roomMap != null) {
            Rect source = new Rect(Math.round(cameraLeft), 0,
                    Math.round(cameraLeft + RoomViewport.ROOM_WIDTH),
                    (int) RoomViewport.ROOM_HEIGHT);
            canvas.drawBitmap(roomMap, source, logicalBounds, bitmapPaint);
            return false;
        }
        int save = canvas.save();
        canvas.translate(-cameraLeft, 0f);
        boolean drawn = drawModularTilemap(canvas);
        canvas.restoreToCount(save);
        if (drawn) return true;
        drawFallbackRoom(canvas);
        return false;
    }

    private boolean drawModularTilemap(Canvas canvas) {
        if (walkMap == null || !walkMap.hasTilemap() || roomTiles == null
                || walkMap.tileAtlasColumns() <= 0) return false;
        int atlasColumns = walkMap.tileAtlasColumns();
        int atlasRows = roomTiles.getHeight() / 16;
        int atlasPixelColumns = roomTiles.getWidth() / 16;
        int availableFrames = Math.min(atlasColumns, atlasPixelColumns) * atlasRows;
        if (roomTiles.getWidth() % 16 != 0 || roomTiles.getHeight() % 16 != 0
                || atlasPixelColumns != atlasColumns || atlasRows != 4) return false;
        for (int row = 0; row < walkMap.tileRows(); row++) {
            for (int column = 0; column < walkMap.tileColumns(); column++) {
                int frame = walkMap.tileAt(column, row);
                if (frame < 0 || frame >= availableFrames) return false;
            }
        }

        Rect source = new Rect();
        RectF destination = new RectF();
        for (int row = 0; row < walkMap.tileRows(); row++) {
            for (int column = 0; column < walkMap.tileColumns(); column++) {
                int frame = walkMap.tileAt(column, row);
                int sourceColumn = frame % atlasColumns;
                int sourceRow = frame / atlasColumns;
                source.set(sourceColumn * 16, sourceRow * 16,
                        sourceColumn * 16 + 16, sourceRow * 16 + 16);
                destination.set(column * 16f, row * 16f,
                        (column + 1) * 16f, (row + 1) * 16f);
                canvas.drawBitmap(roomTiles, source, destination, bitmapPaint);
            }
        }
        return true;
    }

    private void drawFallbackRoom(Canvas canvas) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(INK);
        canvas.drawRect(0f, 0f, 288f, 144f, shapePaint);
        shapePaint.setColor(PANEL);
        canvas.drawRect(0f, 0f, 288f, 24f, shapePaint);
        canvas.drawRect(0f, 136f, 288f, 144f, shapePaint);
        canvas.drawRect(0f, 24f, 24f, 136f, shapePaint);
        canvas.drawRect(264f, 24f, 288f, 136f, shapePaint);

        shapePaint.setColor(FLOOR);
        canvas.drawRect(32f, 32f, 256f, 136f, shapePaint);
        shapePaint.setColor(DECOR);
        for (int x = 32; x <= 256; x += 16) canvas.drawRect(x, 32f, x + 1f, 136f, shapePaint);
        for (int y = 32; y <= 136; y += 16) canvas.drawRect(32f, y, 256f, y + 1f, shapePaint);

        // A restrained containment-room frame keeps the waiting-for-art scene legible.
        shapePaint.setColor(MINT);
        canvas.drawRect(24f, 23f, 264f, 25f, shapePaint);
        shapePaint.setColor(AMBER);
        canvas.drawRect(132f, 24f, 156f, 27f, shapePaint);
    }

    private void drawClue(Canvas canvas, ClueView clue, boolean rotateForPortrait,
                          float cameraLeft) {
        PointF point = RoomViewport.displayPoint(
                clue.getX(), clue.getY(), rotateForPortrait, cameraLeft);
        float x = point.x;
        float y = point.y;
        int index = walkMap == null ? clueIndex(clue.getId())
                : walkMap.clueFrameFor(clue.getId(), clueIndex(clue.getId()));
        if (clueAtlas != null && clueAtlas.getWidth() >= 48 && clueAtlas.getHeight() >= 16
                && index >= 0) {
            Rect src = new Rect(index * 16, 0, index * 16 + 16, 16);
            RectF dst = new RectF(x - 8f, y - 8f, x + 8f, y + 8f);
            canvas.drawBitmap(clueAtlas, src, dst, bitmapPaint);
        } else {
            drawPixelClue(canvas, x, y);
        }

        float badgeHeight = logicalBadgeHeight();
        float badgeCenterY = y - 8f - 2f - badgeHeight / 2f;
        if (clue.isExamined()) {
            drawBadge(canvas, "✓ " + clueBadgeLabel(clue), x, badgeCenterY,
                    IVORY, PANEL);
        } else {
            drawBadge(canvas, clueBadgeLabel(clue), x, badgeCenterY, AMBER, PANEL);
        }
    }

    private void drawPixelClue(Canvas canvas, float x, float y) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(AMBER);
        canvas.drawRect(x - 5f, y - 5f, x + 5f, y + 5f, shapePaint);
        shapePaint.setColor(PANEL);
        canvas.drawRect(x - 2f, y - 2f, x + 2f, y + 2f, shapePaint);
        shapePaint.setColor(IVORY);
        canvas.drawRect(x - 1f, y - 7f, x + 1f, y + 7f, shapePaint);
        canvas.drawRect(x - 7f, y - 1f, x + 7f, y + 1f, shapePaint);
    }

    private void drawCharacter(Canvas canvas, CharacterView character,
                               boolean rotateForPortrait, float cameraLeft) {
        PointF point = RoomViewport.displayPoint(
                character.getX(), character.getY(), rotateForPortrait, cameraLeft);
        float x = point.x;
        float y = point.y;
        int index = walkMap == null ? characterIndex(character.getId())
                : walkMap.characterFrameFor(character.getId(), characterIndex(character.getId()));
        if (survivorAtlas != null && survivorAtlas.getWidth() >= 80
                && survivorAtlas.getHeight() >= 24 && index >= 0) {
            Rect src = new Rect(index * 16, 0, index * 16 + 16, 24);
            RectF dst = new RectF(x - 8f, y - 23f, x + 8f, y + 1f);
            canvas.drawBitmap(survivorAtlas, src, dst, bitmapPaint);
        } else {
            drawPixelCharacter(canvas, x, y, character.isVictim());
        }

        float badgeHeight = logicalBadgeHeight();
        float badgeCenterY = badgeCenterBelowFoot(y, badgeHeight);
        if (character.isVictim()) {
            drawVictimBadge(canvas, x, badgeCenterY);
        } else if ("captain_america".equals(character.getId())) {
            drawBadge(canvas, characterBadgeLabel(character), x - 32f, y,
                    IVORY, PANEL);
        } else {
            drawBadge(canvas, characterBadgeLabel(character),
                    x, badgeCenterY, IVORY, PANEL);
        }
        if (isNearPlayer(character.getX(), character.getY()) && character.canInterview()) {
            drawAnchorOutline(canvas, x, y, MINT);
        }
    }

    private void drawProps(Canvas canvas) {
        if (walkMap == null) return;
        Rect source = new Rect();
        for (RoomWalkMap.PropPlacement prop : walkMap.props()) {
            if (!"props".equals(prop.layer)) continue;
            RectF bounds = new RectF(prop.column * 16f, prop.row * 16f,
                    prop.column * 16f + prop.width, prop.row * 16f + prop.height);
            if ("room_tactical_briefing_table.png".equals(prop.atlas)) {
                if (briefingTable != null) {
                    canvas.drawBitmap(briefingTable, null, bounds, bitmapPaint);
                }
                continue;
            }
            if (roomProps == null) continue;
            int sourceLeft = prop.atlasX * 16;
            int sourceTop = prop.atlasY * 16;
            if (sourceLeft < 0 || sourceTop < 0
                    || sourceLeft + 16 > roomProps.getWidth()
                    || sourceTop + 16 > roomProps.getHeight()) continue;
            source.set(sourceLeft, sourceTop, sourceLeft + 16, sourceTop + 16);
            canvas.drawBitmap(roomProps, source, bounds, bitmapPaint);
        }
    }

    private void drawVictimBadge(Canvas canvas, float anchorX, float anchorY) {
        String label = getResources().getString(R.string.room_victim_badge);
        Paint.FontMetrics metrics = setCanvasTextSize(12f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float width = Math.min(62f, textPaint.measureText(label) + 10f);
        float displayWidth = RoomViewport.displayWidth(portraitRotation);
        float x = Math.max(width / 2f + 1f,
                Math.min(displayWidth - 1f - width / 2f, anchorX));
        float height = badgeHeight(metrics);
        float top = badgeTop(anchorY, height);
        RectF plate = new RectF(x - width / 2f, top, x + width / 2f, top + height);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(IVORY);
        canvas.drawRect(plate, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(0.9f);
        shapePaint.setColor(CORAL);
        canvas.drawRect(plate, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(INK);
        canvas.drawText(label, x, textBaseline(top, height, metrics), textPaint);
    }

    private void drawPixelCharacter(Canvas canvas, float x, float footY, boolean victim) {
        int suit = victim ? IVORY : MINT;
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(PANEL);
        canvas.drawRect(x - 7f, footY - 22f, x + 7f, footY, shapePaint);
        shapePaint.setColor(suit);
        canvas.drawRect(x - 5f, footY - 21f, x + 5f, footY - 9f, shapePaint);
        canvas.drawRect(x - 6f, footY - 8f, x + 6f, footY - 3f, shapePaint);
        shapePaint.setColor(IVORY);
        canvas.drawRect(x - 4f, footY - 23f, x + 4f, footY - 17f, shapePaint);
        shapePaint.setColor(INK);
        canvas.drawRect(x - 3f, footY - 21f, x - 1f, footY - 20f, shapePaint);
        canvas.drawRect(x + 1f, footY - 21f, x + 3f, footY - 20f, shapePaint);
    }

    private void drawPlayer(Canvas canvas, boolean rotateForPortrait, float cameraLeft) {
        if (state == null) return;
        float x = Float.isNaN(animatedPlayerX)
                ? state.getPlayer().getX() : animatedPlayerX;
        float y = Float.isNaN(animatedPlayerY)
                ? state.getPlayer().getY() : animatedPlayerY;
        PointF point = RoomViewport.displayPoint(x, y, rotateForPortrait, cameraLeft);
        float worldX = point.x;
        float worldY = point.y;
        int frame = movementAnimator == null ? 0 : movementFrame;
        if (playerSheet != null && playerSheet.getWidth() >= 64
                && playerSheet.getHeight() >= 96) {
            int displayFacingRow = facingRowForDisplay(rotateForPortrait);
            Rect src = new Rect(frame * 16, displayFacingRow * 24,
                    frame * 16 + 16, displayFacingRow * 24 + 24);
            RectF dst = new RectF(worldX - 8f, worldY - 23f,
                    worldX + 8f, worldY + 1f);
            canvas.drawBitmap(playerSheet, src, dst, bitmapPaint);
        } else {
            drawPixelCharacter(canvas, worldX, worldY, false);
        }
        drawBadge(canvas, getResources().getString(R.string.room_player_marker),
                worldX, badgeCenterBelowFoot(worldY, logicalBadgeHeight()), IVORY, PANEL);
    }

    private void drawNearbyAction(Canvas canvas, boolean rotateForPortrait) {
        if (state == null) return;
        float playerX = Float.isNaN(animatedPlayerX)
                ? state.getPlayer().getX() : animatedPlayerX;
        float playerY = Float.isNaN(animatedPlayerY)
                ? state.getPlayer().getY() : animatedPlayerY;
        float bestDistance = RoomViewport.interactionRadiusSquared();
        String prompt = null;
        for (CharacterView character : state.getCharacters()) {
            if (!character.canInterview()) continue;
            float distance = RoomViewport.worldDistanceSquared(playerX, playerY,
                    character.getX(), character.getY());
            if (distance <= bestDistance) {
                bestDistance = distance;
                prompt = getResources().getString(R.string.room_action_interview);
            }
        }
        for (ClueView clue : state.getClues()) {
            float distance = RoomViewport.worldDistanceSquared(
                    playerX, playerY, clue.getX(), clue.getY());
            if (distance <= bestDistance) {
                bestDistance = distance;
                prompt = getResources().getString(R.string.room_action_examine);
            }
        }
        if (prompt == null) return;
        Paint.FontMetrics metrics = setCanvasTextSize(12f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float displayWidth = RoomViewport.displayWidth(rotateForPortrait);
        float displayHeight = RoomViewport.displayHeight(rotateForPortrait);
        float maxPromptWidth = rotateForPortrait ? displayWidth - 2f : 220f;
        float width = Math.min(maxPromptWidth, textPaint.measureText(prompt) + 10f);
        float height = badgeHeight(metrics);
        float top = Math.max(1f, displayHeight - 1f - height);
        RectF box = new RectF((displayWidth - width) / 2f, top,
                (displayWidth + width) / 2f, top + height);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(PANEL);
        canvas.drawRect(box, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.2f);
        shapePaint.setColor(MINT);
        canvas.drawRect(box, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(IVORY);
        canvas.drawText(prompt, displayWidth / 2f,
                textBaseline(top, height, metrics), textPaint);
    }

    private void drawAnchorOutline(Canvas canvas, float x, float footY, int color) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1f);
        shapePaint.setColor(color);
        canvas.drawRect(x - 9f, footY - 24f, x + 9f, footY + 2f, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
    }

    private void drawBadge(Canvas canvas, String value, float anchorX, float anchorY,
                           int textColor, int accentColor) {
        if (value == null || value.isEmpty()) return;
        Paint.FontMetrics metrics = setCanvasTextSize(12f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(textColor);
        String fitted = fitBadgeText(value, 54f);
        float width = Math.min(60f, textPaint.measureText(fitted) + 6f);
        float displayWidth = RoomViewport.displayWidth(portraitRotation);
        float x = Math.max(width / 2f + 1f,
                Math.min(displayWidth - 1f - width / 2f, anchorX));
        float height = badgeHeight(metrics);
        float top = badgeTop(anchorY, height);
        RectF rect = new RectF(x - width / 2f, top, x + width / 2f, top + height);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(PANEL);
        canvas.drawRect(rect, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(0.75f);
        shapePaint.setColor(accentColor);
        canvas.drawRect(rect, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        canvas.drawText(fitted, x, textBaseline(top, height, metrics), textPaint);
    }

    private Paint.FontMetrics setCanvasTextSize(float sizeSp) {
        float mapScale = RoomViewport.scale(mapRect, portraitRotation);
        if (mapScale <= 0f) mapScale = 1f;
        textPaint.setTextSize(sizeSp * scaledDensity / mapScale);
        return textPaint.getFontMetrics();
    }

    private float badgeHeight(Paint.FontMetrics metrics) {
        float mapScale = RoomViewport.scale(mapRect, portraitRotation);
        if (mapScale <= 0f) mapScale = 1f;
        float minimum = 16f * scaledDensity / mapScale;
        float measured = metrics.descent - metrics.ascent + 4f;
        return Math.max(minimum, measured);
    }

    private float logicalBadgeHeight() {
        return badgeHeight(setCanvasTextSize(12f));
    }

    private float badgeCenterBelowFoot(float footY, float height) {
        float displayHeight = RoomViewport.displayHeight(portraitRotation);
        if (footY + 2f + height <= displayHeight - 1f) {
            return footY + 2f + height / 2f;
        }
        return footY - 23f - 2f - height / 2f;
    }

    private float badgeTop(float centerY, float height) {
        float displayHeight = RoomViewport.displayHeight(portraitRotation);
        return Math.max(1f,
                Math.min(displayHeight - 1f - height, centerY - height / 2f));
    }

    private float textBaseline(float top, float height, Paint.FontMetrics metrics) {
        float glyphHeight = metrics.descent - metrics.ascent;
        return top + (height - glyphHeight) / 2f - metrics.ascent;
    }

    private String fitBadgeText(String value, float maximumTextWidth) {
        String fitted = value;
        while (fitted.length() > 1 && textPaint.measureText(fitted) > maximumTextWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted;
    }

    private void updateMovementPosition(List<PointF> path, float[] lengths, float distance) {
        float remaining = distance;
        for (int i = 0; i < lengths.length; i++) {
            float segment = lengths[i];
            if (remaining <= segment || i == lengths.length - 1) {
                float fraction = segment <= 0f ? 1f : Math.min(1f, remaining / segment);
                PointF from = path.get(i);
                PointF to = path.get(i + 1);
                animatedPlayerX = from.x + (to.x - from.x) * fraction;
                animatedPlayerY = from.y + (to.y - from.y) * fraction;
                updateFacing(from, to);
                return;
            }
            remaining -= segment;
        }
    }

    private void updateFacing(PointF from, PointF to) {
        float dx = (to.x - from.x) * RoomViewport.LOGICAL_WIDTH;
        float dy = (to.y - from.y) * RoomViewport.LOGICAL_HEIGHT;
        if (Math.abs(dx) > Math.abs(dy)) facingRow = dx < 0f ? 1 : 2;
        else if (Math.abs(dy) > 0.0001f) facingRow = dy < 0f ? 3 : 0;
    }

    /** Maps logical movement direction to the upright sprite row on the rotated portrait view. */
    private int facingRowForDisplay(boolean rotateForPortrait) {
        if (!rotateForPortrait) return facingRow;
        switch (facingRow) {
            case 0: return 1; // logical down becomes screen left
            case 1: return 3; // logical left becomes screen up
            case 2: return 0; // logical right becomes screen down
            case 3: return 2; // logical up becomes screen right
            default: return facingRow;
        }
    }

    private void resetAnimatedPlayerToState() {
        movementFrame = 0;
        if (state == null) {
            animatedPlayerX = Float.NaN;
            animatedPlayerY = Float.NaN;
        } else {
            animatedPlayerX = state.getPlayer().getX();
            animatedPlayerY = state.getPlayer().getY();
        }
    }

    private Bitmap loadDrawableBitmap(String resourceName) {
        int id = getResources().getIdentifier(resourceName, "drawable", getContext().getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private void computeMapRect() {
        int orientation = getResources().getConfiguration().orientation;
        portraitRotation = orientation == Configuration.ORIENTATION_PORTRAIT
                || (orientation == Configuration.ORIENTATION_UNDEFINED
                && getHeight() > getWidth());
        RectF rect = RoomViewport.mapRect(getWidth(), getHeight(), portraitRotation);
        mapRect.set(rect);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || listener == null
                || state == null || getWidth() == 0 || getHeight() == 0) return true;
        if (movementAnimator != null) return true;
        computeMapRect();
        if (!mapRect.contains(event.getX(), event.getY())) {
            performClick();
            return true;
        }

        CharacterView character = nearestCharacter(event.getX(), event.getY());
        ClueView clue = nearestClue(event.getX(), event.getY());
        if (character != null && (clue == null
                || RoomViewport.screenDepth(character.getX(), character.getY(), portraitRotation)
                >= RoomViewport.screenDepth(clue.getX(), clue.getY(), portraitRotation))) {
            listener.onCharacterTapped(character);
            performClick();
            return true;
        }
        if (clue != null) {
            listener.onClueTapped(clue);
            performClick();
            return true;
        }

        PointF worldPoint = RoomViewport.normalizedPoint(
                mapRect, event.getX(), event.getY(), portraitRotation, cameraLeft());
        listener.onFloorTapped(worldPoint.x, worldPoint.y);
        performClick();
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private CharacterView nearestCharacter(float screenX, float screenY) {
        CharacterView nearest = null;
        float bestDepth = -Float.MAX_VALUE;
        float bestDistance = Float.MAX_VALUE;
        float scale = RoomViewport.scale(mapRect, portraitRotation);
        float targetSize = 48f * density;
        float cameraLeft = cameraLeft();
        for (CharacterView character : state.getCharacters()) {
            PointF anchor = RoomViewport.screenPoint(
                    mapRect, character.getX(), character.getY(), portraitRotation, cameraLeft);
            float anchorX = anchor.x;
            float footY = anchor.y;
            float width = Math.max(targetSize, 16f * scale);
            float height = Math.max(targetSize, 24f * scale);
            float centerY = footY;
            RectF bounds = new RectF(anchorX - width / 2f, centerY - height / 2f,
                    anchorX + width / 2f, centerY + height / 2f);
            if (!bounds.contains(screenX, screenY)) continue;
            float dx = screenX - anchorX;
            float dy = screenY - centerY;
            float distance = dx * dx + dy * dy;
            float depth = RoomViewport.screenDepth(
                    character.getX(), character.getY(), portraitRotation);
            if (depth > bestDepth
                    || (depth == bestDepth && distance < bestDistance)) {
                nearest = character;
                bestDepth = depth;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private ClueView nearestClue(float screenX, float screenY) {
        ClueView nearest = null;
        float bestDepth = -Float.MAX_VALUE;
        float bestDistance = Float.MAX_VALUE;
        float scale = RoomViewport.scale(mapRect, portraitRotation);
        float targetSize = 48f * density;
        float cameraLeft = cameraLeft();
        for (ClueView clue : state.getClues()) {
            PointF anchor = RoomViewport.screenPoint(
                    mapRect, clue.getX(), clue.getY(), portraitRotation, cameraLeft);
            float x = anchor.x;
            float y = anchor.y;
            float size = Math.max(targetSize, 16f * scale);
            RectF bounds = new RectF(x - size / 2f, y - size / 2f,
                    x + size / 2f, y + size / 2f);
            if (!bounds.contains(screenX, screenY)) continue;
            float dx = screenX - x;
            float dy = screenY - y;
            float distance = dx * dx + dy * dy;
            float depth = RoomViewport.screenDepth(
                    clue.getX(), clue.getY(), portraitRotation);
            if (depth > bestDepth
                    || (depth == bestDepth && distance < bestDistance)) {
                nearest = clue;
                bestDepth = depth;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean isNearPlayer(float x, float y) {
        float playerX = Float.isNaN(animatedPlayerX)
                ? state.getPlayer().getX() : animatedPlayerX;
        float playerY = Float.isNaN(animatedPlayerY)
                ? state.getPlayer().getY() : animatedPlayerY;
        return RoomViewport.withinInteractionRange(playerX, playerY, x, y);
    }

    private float cameraLeft() {
        float playerX = Float.isNaN(animatedPlayerX)
                ? state == null ? 0.5f : state.getPlayer().getX()
                : animatedPlayerX;
        return RoomViewport.cameraLeft(playerX);
    }

    private static float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private String characterBadgeLabel(CharacterView character) {
        switch (character.getId()) {
            case "iron_man": return getResources().getString(R.string.room_badge_iron_man);
            case "captain_america":
                return getResources().getString(R.string.room_badge_captain_america);
            case "thor": return getResources().getString(R.string.room_badge_thor);
            case "black_widow": return getResources().getString(R.string.room_badge_black_widow);
            case "hawkeye": return getResources().getString(R.string.room_badge_hawkeye);
            default: return compactLabel(character.getDisplayName(), 7);
        }
    }

    private String clueBadgeLabel(ClueView clue) {
        switch (clue.getId()) {
            case "clue_access_log": return getResources().getString(R.string.room_badge_access_log);
            case "clue_seal_trace": return getResources().getString(R.string.room_badge_seal_trace);
            case "clue_reagent_sample":
                return getResources().getString(R.string.room_badge_reagent_sample);
            default: return compactLabel(clue.getTitle(), 7);
        }
    }

    private static String compactLabel(String value, int limit) {
        if (value == null) return "";
        String oneLine = value.replace('\n', ' ').trim();
        return oneLine.length() <= limit ? oneLine
                : oneLine.substring(0, limit - 1) + "…";
    }

    private static int characterIndex(String id) {
        if ("iron_man".equals(id)) return 0;
        if ("captain_america".equals(id)) return 1;
        if ("thor".equals(id)) return 2;
        if ("black_widow".equals(id)) return 3;
        if ("hawkeye".equals(id)) return 4;
        return -1;
    }

    private static int clueIndex(String id) {
        if ("clue_access_log".equals(id)) return 0;
        if ("clue_seal_trace".equals(id)) return 1;
        if ("clue_reagent_sample".equals(id)) return 2;
        return -1;
    }

    private static final class RenderEntity {
        final float footY;
        final CharacterView character;
        final ClueView clue;

        private RenderEntity(float footY, CharacterView character, ClueView clue) {
            this.footY = footY;
            this.character = character;
            this.clue = clue;
        }

        static RenderEntity forCharacter(CharacterView character, float footY) {
            return new RenderEntity(footY, character, null);
        }

        static RenderEntity forClue(ClueView clue, float anchorY) {
            return new RenderEntity(anchorY, null, clue);
        }

        static RenderEntity forPlayer(float footY) {
            return new RenderEntity(footY, null, null);
        }
    }
}
