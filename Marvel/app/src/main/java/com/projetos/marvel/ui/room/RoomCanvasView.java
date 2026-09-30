package com.projetos.marvel.ui.room;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.projetos.marvel.R;
import com.projetos.marvel.caseengine.CharacterView;
import com.projetos.marvel.caseengine.ClueView;
import com.projetos.marvel.caseengine.RoomViewState;

public final class RoomCanvasView extends View {
    public interface Listener {
        void onFloorTapped(float x, float y);
        void onCharacterTapped(CharacterView character);
        void onClueTapped(ClueView clue);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable background;
    private final Drawable characterToken;
    private final Drawable clueMarker;
    private RoomViewState state;
    private Listener listener;

    public RoomCanvasView(Context context) { this(context, null); }

    public RoomCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        background = context.getDrawable(R.drawable.room_background);
        characterToken = context.getDrawable(R.drawable.room_character_token);
        clueMarker = context.getDrawable(R.drawable.room_clue_marker);
        setClickable(true);
        paint.setColor(0xFFF4FAF8);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(3f, 0f, 2f, 0xCC000000);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void render(RoomViewState state) {
        this.state = state;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (background != null) {
            background.setBounds(0, 0, getWidth(), getHeight());
            background.draw(canvas);
        }
        if (state == null || getWidth() == 0 || getHeight() == 0) return;

        float density = getResources().getDisplayMetrics().density;
        paint.setTextSize(11f * density);
        for (ClueView clue : state.getClues()) {
            float x = clue.getX() * getWidth();
            float y = clue.getY() * getHeight();
            drawIcon(canvas, clueMarker, x, y, 28f * density, 32f * density);
            canvas.drawText(clue.getTitle() + (clue.isExamined() ? " ✓" : ""),
                    x, y + 25f * density, paint);
        }
        for (CharacterView character : state.getCharacters()) {
            float x = character.getX() * getWidth();
            float y = character.getY() * getHeight();
            drawIcon(canvas, characterToken, x, y, 37f * density, 44f * density);
            canvas.drawText(character.getDisplayName(), x, y + 31f * density, paint);
            if (character.isVictim()) {
                paint.setColor(0xFFF2BD58);
                canvas.drawText("VÍTIMA", x, y + 45f * density, paint);
                paint.setColor(0xFFF4FAF8);
            }
        }
        float px = state.getPlayer().getX() * getWidth();
        float py = state.getPlayer().getY() * getHeight();
        paint.setColor(0xFF72F0DB);
        canvas.drawCircle(px, py, 16f * density, paint);
        paint.setColor(0xFF10212E);
        canvas.drawCircle(px, py, 10f * density, paint);
        paint.setColor(0xFFF4FAF8);
        canvas.drawText("VOCÊ", px, py + 29f * density, paint);
    }

    private static void drawIcon(Canvas canvas, Drawable icon, float x, float y,
                                 float width, float height) {
        if (icon == null) return;
        icon.setBounds(Math.round(x - width / 2f), Math.round(y - height / 2f),
                Math.round(x + width / 2f), Math.round(y + height / 2f));
        icon.draw(canvas);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || listener == null
                || state == null || getWidth() == 0 || getHeight() == 0) return true;
        float nx = clamp(event.getX() / getWidth());
        float ny = clamp(event.getY() / getHeight());
        CharacterView character = nearestCharacter(nx, ny);
        if (character != null) {
            listener.onCharacterTapped(character);
            performClick();
            return true;
        }
        ClueView clue = nearestClue(nx, ny);
        if (clue != null) {
            listener.onClueTapped(clue);
            performClick();
            return true;
        }
        listener.onFloorTapped(nx, ny);
        performClick();
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private CharacterView nearestCharacter(float x, float y) {
        CharacterView nearest = null;
        float limit = 0.065f * 0.065f;
        for (CharacterView character : state.getCharacters()) {
            float distance = distanceSquared(x, y, character.getX(), character.getY());
            if (distance < limit) {
                nearest = character;
                limit = distance;
            }
        }
        return nearest;
    }

    private ClueView nearestClue(float x, float y) {
        ClueView nearest = null;
        float limit = 0.065f * 0.065f;
        for (ClueView clue : state.getClues()) {
            float distance = distanceSquared(x, y, clue.getX(), clue.getY());
            if (distance < limit) {
                nearest = clue;
                limit = distance;
            }
        }
        return nearest;
    }

    private static float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
