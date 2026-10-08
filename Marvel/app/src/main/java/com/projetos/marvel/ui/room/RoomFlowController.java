package com.projetos.marvel.ui.room;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;

import com.projetos.marvel.R;
import com.projetos.marvel.caseengine.AccusationResult;
import com.projetos.marvel.caseengine.CaseEngine;
import com.projetos.marvel.caseengine.CaseSession;
import com.projetos.marvel.caseengine.CharacterView;
import com.projetos.marvel.caseengine.ClueView;
import com.projetos.marvel.caseengine.InterviewDossier;
import com.projetos.marvel.caseengine.RoomViewState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class RoomFlowController {
    public interface ResultListener {
        void onCaseFinished(AccusationResult result);
    }
    private static final String HOTBAR_NOTEBOOK = "notebook";
    private static final String HOTBAR_ACCUSATION = "accusation";
    private static final int INK = 0xFF0D1720;
    private static final int PANEL = 0xFF243543;
    private static final int DECOR = 0xFF52666A;
    private static final int IVORY = 0xFFC4D5C8;
    private static final int MINT = 0xFF44CFBD;

    private Activity activity;
    private View root;
    private final Random random = new Random();
    private final Map<String, List<InterviewSender.ChatTurn>> histories = new HashMap<>();
    private final Map<String, InterviewDossier> knownDossiers = new HashMap<>();
    private final Map<String, PendingInterview> pending = new HashMap<>();
    private final Map<Integer, Bitmap> clueSlotBitmaps = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private RoomCanvasView canvas;
    private RoomWalkMap walkMap;
    private ImageButton notebookButton;
    private ImageButton accusationButton;
    private HorizontalScrollView clueScroll;
    private LinearLayout clueSlotContainer;
    private CaseSession session;
    private long seed = -1L;
    private long caseGeneration;
    private boolean resultShown;
    private ResultListener resultListener;
    private Dialog activeDialog;
    private AlertDialog activeAccusationChoices;
    private InterviewSender interviewSender;
    private String activeInterviewId;
    private String retainedInterviewId;
    private String selectedHotbarItem;
    private String selectedSuspectId;
    private int hotbarClueScrollX;
    private boolean retainedAccusationDialog;
    private LinearLayout activeTranscript;
    private ScrollView activeTranscriptScroll;
    private LinearLayout activeInterviewStatusPanel;
    private TextView activeInterviewStatus;
    private View activeInterviewStatusMarker;
    private Button activeRetry;
    private Button activeSend;
    private Bitmap clueAtlas;

    public RoomFlowController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
    }

    public void bind() {
        bindViews();
        startNewCase();
    }

    /** Rebinds the retained case/controller to the Activity recreated by an orientation change. */
    public void attach(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        bindViews();
        if (session == null) {
            startNewCase();
            return;
        }
        render();
        String resumeId = retainedInterviewId;
        retainedInterviewId = null;
        InterviewDossier dossier = resumeId == null ? null : knownDossiers.get(resumeId);
        if (dossier != null) showInterview(dossier);
        else if (retainedAccusationDialog) {
            retainedAccusationDialog = false;
            showAccusationChoices();
        }
    }

    private void bindViews() {
        canvas = root.findViewById(R.id.room_canvas);
        walkMap = new RoomWalkMap(activity);
        canvas.setWalkMap(walkMap);
        notebookButton = root.findViewById(R.id.btn_notebook);
        accusationButton = root.findViewById(R.id.btn_accusation_slot);
        configureHotbarIcon(notebookButton);
        configureHotbarIcon(accusationButton);
        clueScroll = root.findViewById(R.id.investigation_clues_scroll);
        clueSlotContainer = root.findViewById(R.id.hotbar_clue_slots);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            clueScroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                    hotbarClueScrollX = scrollX);
        } else {
            clueScroll.getViewTreeObserver().addOnScrollChangedListener(
                    () -> hotbarClueScrollX = clueScroll.getScrollX());
        }
        canvas.setListener(new RoomCanvasView.Listener() {
            @Override public void onFloorTapped(float x, float y) {
                walkToNormalized(x, y);
            }
            @Override public void onCharacterTapped(CharacterView character) {
                handleCharacterTap(character);
            }
            @Override public void onClueTapped(ClueView clue) {
                handleClueTap(clue);
            }
        });
        notebookButton.setOnClickListener(v -> {
            selectHotbarItem(HOTBAR_NOTEBOOK);
            showNotebook();
        });
        accusationButton.setOnClickListener(v -> {
            selectHotbarItem(HOTBAR_ACCUSATION);
            showAccusationChoices();
        });
    }

    private void configureHotbarIcon(ImageButton button) {
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (button.getDrawable() instanceof BitmapDrawable) {
            ((BitmapDrawable) button.getDrawable()).setFilterBitmap(false);
        }
    }

    public void setInterviewSender(InterviewSender sender) {
        interviewSender = sender;
    }

    public void setResultListener(ResultListener listener) {
        resultListener = listener;
    }

    public AccusationResult getAccusationResult() {
        return session == null ? null
                : session.getRoomViewState().getAccusationResult().orElse(null);
    }

    public boolean handleBack() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            return true;
        }
        return false;
    }

    /** Keeps the live session and interview requests across Activity configuration changes. */
    public void retainForConfigurationChange() {
        if (canvas != null && session != null) {
            PointF position = canvas.getCurrentPlayerPosition();
            canvas.cancelMovement();
            session.movePlayerTo(position.x, position.y);
        }
        retainedInterviewId = activeInterviewId;
        retainedAccusationDialog = activeAccusationChoices != null
                && activeAccusationChoices.isShowing();
        Dialog previousDialog = activeDialog;
        activeDialog = null;
        activeInterviewId = null;
        clearActiveInterviewViews();
        if (previousDialog != null) previousDialog.dismiss();
    }

    private void clearActiveInterviewViews() {
        activeTranscript = null;
        activeTranscriptScroll = null;
        activeInterviewStatusPanel = null;
        activeInterviewStatus = null;
        activeInterviewStatusMarker = null;
        activeRetry = null;
        activeSend = null;
    }

    public void startNewCase() {
        if (canvas != null) canvas.cancelMovement();
        caseGeneration++;
        long nextSeed = random.nextInt(20);
        if (seed >= 0 && nextSeed == seed) nextSeed = (nextSeed + 1) % 20L;
        seed = nextSeed;
        resultShown = false;
        selectedHotbarItem = null;
        selectedSuspectId = null;
        hotbarClueScrollX = 0;
        retainedAccusationDialog = false;
        session = CaseEngine.newSession(seed);
        histories.clear();
        knownDossiers.clear();
        pending.clear();
        inFlight.clear();
        retainedInterviewId = null;
        render();
    }

    private void render() {
        RoomViewState state = session.getRoomViewState();
        canvas.render(state);
        boolean finished = state.getAccusationResult().isPresent();
        accusationButton.setEnabled(!finished);
        renderHotbar(state);
        state.getAccusationResult().ifPresent(result -> {
            if (!resultShown) {
                resultShown = true;
                if (resultListener != null) resultListener.onCaseFinished(result);
            }
        });
    }

    private void handleCharacterTap(CharacterView character) {
        if (!character.canInterview()) {
            showRoomFeedback(R.string.victim_cannot_interview);
            return;
        }
        if (!isNear(character.getX(), character.getY())) {
            showRoomFeedback(R.string.room_move_closer_character);
            return;
        }
        try {
            InterviewDossier dossier = session.getInterviewDossier(character.getId());
            knownDossiers.put(character.getId(), dossier);
            showInterview(dossier);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoomFeedback(exception.getMessage());
        }
    }

    private void handleClueTap(ClueView clue) {
        if (!isNear(clue.getX(), clue.getY())) {
            showRoomFeedback(R.string.room_move_closer_clue);
            return;
        }
        try {
            ClueView examined = session.examine(clue.getId());
            selectHotbarItem(clueHotbarKey(examined));
            render();
            showClueDetails(examined);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showRoomFeedback(exception.getMessage());
        }
    }

    private void showClueDetails(ClueView clue) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(clue.getTitle())
                .setMessage(clue.getDescription())
                .setPositiveButton(R.string.close, null)
                .show();
        styleAlertDialog(dialog);
        track(dialog);
    }

    private void renderHotbar(RoomViewState state) {
        clueSlotContainer.removeAllViews();
        int examinedCount = 0;
        for (ClueView clue : state.getClues()) {
            if (!clue.isExamined()) continue;
            examinedCount++;
            ImageButton slot = (ImageButton) LayoutInflater.from(activity).inflate(
                    R.layout.view_hotbar_clue_slot, clueSlotContainer, false);
            slot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            BitmapDrawable clueDrawable = clueDrawableForHotbar(clue);
            if (clueDrawable != null) slot.setImageDrawable(clueDrawable);
            slot.setContentDescription(activity.getString(
                    R.string.hotbar_clue_description, clue.getTitle()));
            String key = clueHotbarKey(clue);
            slot.setTag(key);
            slot.setSelected(key.equals(selectedHotbarItem));
            slot.setOnClickListener(view -> {
                selectHotbarItem(key);
                showClueDetails(clue);
            });
            clueSlotContainer.addView(slot);
        }
        for (int slotIndex = examinedCount; slotIndex < 5; slotIndex++) {
            View emptySlot = LayoutInflater.from(activity).inflate(
                    R.layout.view_hotbar_empty_slot, clueSlotContainer, false);
            clueSlotContainer.addView(emptySlot);
        }
        notebookButton.setSelected(HOTBAR_NOTEBOOK.equals(selectedHotbarItem));
        accusationButton.setSelected(HOTBAR_ACCUSATION.equals(selectedHotbarItem));
        clueScroll.post(() -> clueScroll.scrollTo(hotbarClueScrollX, 0));
    }

    private void selectHotbarItem(String key) {
        selectedHotbarItem = key;
        notebookButton.setSelected(HOTBAR_NOTEBOOK.equals(key));
        accusationButton.setSelected(HOTBAR_ACCUSATION.equals(key));
        for (int i = 0; i < clueSlotContainer.getChildCount(); i++) {
            View slot = clueSlotContainer.getChildAt(i);
            slot.setSelected(key != null && key.equals(slot.getTag()));
        }
    }

    private String clueHotbarKey(ClueView clue) {
        return "clue:" + clue.getId();
    }

    private BitmapDrawable clueDrawableForHotbar(ClueView clue) {
        int fallbackFrame;
        switch (clue.getId()) {
            case "clue_access_log": fallbackFrame = 0; break;
            case "clue_seal_trace": fallbackFrame = 1; break;
            case "clue_reagent_sample": fallbackFrame = 2; break;
            default: return null;
        }
        int frame = walkMap == null ? fallbackFrame
                : walkMap.clueFrameFor(clue.getId(), fallbackFrame);
        if (frame < 0 || frame >= 3) return null;
        Bitmap icon = clueSlotBitmaps.get(frame);
        if (icon == null) {
            if (clueAtlas == null) {
                clueAtlas = BitmapFactory.decodeResource(
                        activity.getResources(), R.drawable.room_clue_atlas_48x16);
            }
            if (clueAtlas == null || clueAtlas.getWidth() < (frame + 1) * 16
                    || clueAtlas.getHeight() < 16) return null;
            icon = Bitmap.createBitmap(clueAtlas, frame * 16, 0, 16, 16);
            clueSlotBitmaps.put(frame, icon);
        }
        BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), icon);
        drawable.setFilterBitmap(false);
        return drawable;
    }

    private boolean isNear(float x, float y) {
        RoomViewState state = session.getRoomViewState();
        return RoomViewport.withinInteractionRange(
                state.getPlayer().getX(), state.getPlayer().getY(), x, y);
    }

    private void walkToNormalized(float x, float y) {
        RoomViewState state = session.getRoomViewState();
        List<PointF> path = walkMap.pathToNormalized(
                state.getPlayer().getX(), state.getPlayer().getY(), x, y);
        animateWalk(path);
    }

    private void animateWalk(List<PointF> path) {
        if (path == null || path.isEmpty()) {
            showRoomFeedback(R.string.room_no_path);
            return;
        }
        canvas.animatePlayerAlong(path, (x, y) -> {
            session.movePlayerTo(x, y);
            render();
        });
    }

    private void showRoomFeedback(int messageResId) {
        Toast.makeText(activity, messageResId, Toast.LENGTH_SHORT).show();
    }

    private void showRoomFeedback(String message) {
        if (message != null && !message.trim().isEmpty()) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showNotebook() {
        RoomViewState state = session.getRoomViewState();
        StringBuilder notes = new StringBuilder(getString(R.string.examined_clues_title));
        boolean foundClue = false;
        for (ClueView clue : state.getClues()) {
            if (!clue.isExamined()) continue;
            foundClue = true;
            notes.append("\n\n• ").append(clue.getTitle()).append("\n")
                    .append(clue.getDescription());
        }
        if (!foundClue) notes.append("\n").append(getString(R.string.no_clues_yet));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.case_notebook)
                .setMessage(notes.toString())
                .setPositiveButton(R.string.close, null)
                .show();
        styleAlertDialog(dialog);
        track(dialog);
    }

    private void showAccusationChoices() {
        selectHotbarItem(HOTBAR_ACCUSATION);
        if (session.getRoomViewState().getAccusationResult().isPresent()) return;
        List<CharacterView> suspects = new ArrayList<>();
        for (CharacterView character : session.getRoomViewState().getCharacters()) {
            if (character.canAccuse()) suspects.add(character);
        }
        if (suspects.isEmpty()) return;
        String[] names = new String[suspects.size()];
        for (int i = 0; i < suspects.size(); i++) names[i] = suspects.get(i).getDisplayName();
        int initialSelection = -1;
        for (int i = 0; i < suspects.size(); i++) {
            if (suspects.get(i).getId().equals(selectedSuspectId)) {
                initialSelection = i;
                break;
            }
        }
        int[] selected = {initialSelection};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.choose_suspect)
                .setSingleChoiceItems(names, initialSelection, (d, which) -> {
                    selected[0] = which;
                    selectedSuspectId = suspects.get(which).getId();
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.accuse, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setEnabled(selected[0] >= 0);
            confirm.setOnClickListener(v -> {
                if (selected[0] < 0) return;
                selectedSuspectId = suspects.get(selected[0]).getId();
                session.accuse(selectedSuspectId);
                dialog.dismiss();
                render();
            });
        });
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
            if (activeAccusationChoices == dialog) activeAccusationChoices = null;
        });
        dialog.show();
        styleAlertDialog(dialog);
        activeAccusationChoices = dialog;
        activeDialog = dialog;
    }

    private void showInterview(InterviewDossier dossier) {
        Dialog dialog = new Dialog(activity);
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_interview, null, false);
        TextView heading = panel.findViewById(R.id.interview_name);
        TextView personality = panel.findViewById(R.id.interview_personality);
        LinearLayout transcript = panel.findViewById(R.id.interview_transcript);
        ScrollView transcriptScroll = panel.findViewById(R.id.interview_transcript_scroll);
        LinearLayout statusPanel = panel.findViewById(R.id.interview_status_panel);
        TextView requestStatus = panel.findViewById(R.id.interview_status);
        View statusMarker = panel.findViewById(R.id.interview_status_marker);
        ImageView portrait = panel.findViewById(R.id.interview_portrait);
        EditText input = panel.findViewById(R.id.interview_input);
        Button send = panel.findViewById(R.id.interview_send);
        Button retry = panel.findViewById(R.id.interview_retry);
        Button close = panel.findViewById(R.id.interview_close);
        ImageButton notebook = panel.findViewById(R.id.interview_notebook);

        heading.setText(dossier.getCharacterName());
        personality.setText(dossier.getPersonality());
        portrait.setContentDescription(activity.getString(
                R.string.interview_portrait_description, dossier.getCharacterName()));
        bindPortrait(portrait, dossier.getCharacterId());
        String id = dossier.getCharacterId();
        activeInterviewId = id;
        activeTranscript = transcript;
        activeTranscriptScroll = transcriptScroll;
        activeInterviewStatusPanel = statusPanel;
        activeInterviewStatus = requestStatus;
        activeInterviewStatusMarker = statusMarker;
        activeRetry = retry;
        activeSend = send;
        renderTranscript(id, transcript, transcriptScroll);

        PendingInterview request = pending.get(id);
        retry.setVisibility(View.GONE);
        statusPanel.setVisibility(View.GONE);
        if (request != null && inFlight.contains(id)) {
            requestStatus.setText(activity.getString(
                    R.string.interview_thinking, dossier.getCharacterName()));
            showInterviewStatus(true);
            retry.setEnabled(false);
            send.setEnabled(false);
        } else if (request != null) {
            requestStatus.setText(R.string.interview_pending_retry);
            showInterviewStatus(false);
            retry.setVisibility(View.VISIBLE);
            retry.setEnabled(true);
        }

        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            if (inFlight.contains(id)) {
                requestStatus.setText(activity.getString(
                        R.string.interview_thinking, dossier.getCharacterName()));
                showInterviewStatus(true);
                return;
            }
            if (pending.containsKey(id)) {
                requestStatus.setText(R.string.retry_before_sending);
                showInterviewStatus(false);
                retry.setVisibility(View.VISIBLE);
                return;
            }
            List<InterviewSender.ChatTurn> history = historyFor(id);
            List<InterviewSender.ChatTurn> oldHistory = new ArrayList<>(history);
            history.add(new InterviewSender.ChatTurn(true, message));
            pending.put(id, new PendingInterview(dossier, oldHistory, message));
            input.setText("");
            renderTranscript(id, transcript, transcriptScroll);
            sendPending(id);
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean sendAction = actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP);
            if (!sendAction) return false;
            send.performClick();
            return true;
        });
        retry.setOnClickListener(v -> sendPending(id));
        close.setOnClickListener(v -> dialog.dismiss());
        notebook.setOnClickListener(v -> {
            dialog.dismiss();
            showNotebook();
        });
        dialog.setContentView(panel);
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
            if (id.equals(activeInterviewId)) {
                activeInterviewId = null;
                clearActiveInterviewViews();
            }
        });
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        int availableWidth = root.getWidth() > 0
                ? root.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        int availableHeight = root.getHeight() > 0
                ? root.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
        int width = Math.max(dp(240), Math.min(availableWidth - dp(24), dp(720)));
        int height = Math.max(dp(220), Math.min(availableHeight - dp(16), dp(680)));
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true);
            window.setBackgroundDrawableResource(R.drawable.interview_dialog_frame);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        activeDialog = dialog;
        dialog.show();
        if (window != null) {
            window.setLayout(width, height);
        }
    }

    private void bindPortrait(ImageView image, String characterId) {
        int frame;
        switch (characterId) {
            case "iron_man": frame = 0; break;
            case "captain_america": frame = 1; break;
            case "thor": frame = 2; break;
            case "black_widow": frame = 3; break;
            case "hawkeye": frame = 4; break;
            default: return;
        }
        Bitmap atlas = BitmapFactory.decodeResource(
                activity.getResources(), R.drawable.survivor_idle_atlas_80x24);
        if (atlas == null || atlas.getWidth() < (frame + 1) * 16 || atlas.getHeight() < 24) return;
        Bitmap portrait = Bitmap.createBitmap(atlas, frame * 16, 0, 16, 24);
        if (atlas != portrait) atlas.recycle();
        BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), portrait);
        drawable.setFilterBitmap(false);
        image.setImageDrawable(drawable);
    }

    private void sendPending(String id) {
        PendingInterview request = pending.get(id);
        final long requestGeneration = caseGeneration;
        if (request == null || inFlight.contains(id)) return;
        if (interviewSender == null) {
            if (isActiveInterview(id)) {
                activeInterviewStatus.setText(R.string.interview_service_unavailable);
                showInterviewStatus(false);
                activeRetry.setEnabled(true);
                activeRetry.setVisibility(View.VISIBLE);
            }
            return;
        }
        inFlight.add(id);
        if (isActiveInterview(id)) {
            activeInterviewStatus.setText(activity.getString(
                    R.string.interview_thinking, request.dossier.getCharacterName()));
            showInterviewStatus(true);
            activeRetry.setEnabled(false);
            activeRetry.setVisibility(View.GONE);
            activeSend.setEnabled(false);
        }
        try {
            interviewSender.send(request.dossier,
                    Collections.unmodifiableList(new ArrayList<>(request.historyBefore)),
                    request.message, new InterviewSender.Callback() {
                        @Override public void onSuccess(String response) {
                            activity.runOnUiThread(() -> {
                                if (caseGeneration != requestGeneration
                                        || pending.get(id) != request) return;
                                inFlight.remove(id);
                                historyFor(id).add(new InterviewSender.ChatTurn(false, response));
                                pending.remove(id);
                                if (isActiveInterview(id)) {
                                    renderTranscript(id, activeTranscript, activeTranscriptScroll);
                                    activeInterviewStatus.setText(R.string.interview_received);
                                    showInterviewStatus(true);
                                    activeRetry.setVisibility(View.GONE);
                                    activeSend.setEnabled(true);
                                }
                            });
                        }
                        @Override public void onFailure(String message) {
                            activity.runOnUiThread(() -> {
                                if (caseGeneration != requestGeneration
                                        || pending.get(id) != request) return;
                                inFlight.remove(id);
                                if (isActiveInterview(id)) {
                                    activeInterviewStatus.setText(activity.getString(
                                            R.string.interview_failed_preserved, message));
                                    showInterviewStatus(false);
                                    activeRetry.setEnabled(true);
                                    activeRetry.setVisibility(View.VISIBLE);
                                    activeSend.setEnabled(true);
                                }
                            });
                        }
                    });
        } catch (RuntimeException exception) {
            if (caseGeneration != requestGeneration || pending.get(id) != request) return;
            inFlight.remove(id);
            if (isActiveInterview(id)) {
                String message = exception.getMessage() == null
                        ? getString(R.string.unknown_error) : exception.getMessage();
                activeInterviewStatus.setText(activity.getString(
                        R.string.interview_failed_preserved, message));
                showInterviewStatus(false);
                activeRetry.setEnabled(true);
                activeRetry.setVisibility(View.VISIBLE);
                activeSend.setEnabled(true);
            }
        }
    }

    private boolean isActiveInterview(String id) {
        return id.equals(activeInterviewId) && activeTranscript != null
                && activeInterviewStatusPanel != null && activeInterviewStatus != null
                && activeInterviewStatusMarker != null && activeRetry != null && activeSend != null;
    }

    private void showInterviewStatus(boolean thinking) {
        if (activeInterviewStatusPanel == null || activeInterviewStatusMarker == null) return;
        activeInterviewStatusPanel.setSelected(thinking);
        activeInterviewStatusMarker.setSelected(thinking);
        activeInterviewStatusPanel.setVisibility(View.VISIBLE);
    }

    private void renderTranscript(String id, LinearLayout target, ScrollView scroll) {
        TextView emptyState = target.findViewById(R.id.interview_transcript_empty);
        for (int childIndex = target.getChildCount() - 1; childIndex >= 0; childIndex--) {
            View child = target.getChildAt(childIndex);
            if (child != emptyState) target.removeViewAt(childIndex);
        }
        List<InterviewSender.ChatTurn> turns = historyFor(id);
        emptyState.setVisibility(turns.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < turns.size(); index++) {
            InterviewSender.ChatTurn turn = turns.get(index);
            boolean fromPlayer = turn.isFromPlayer();
            View row = LayoutInflater.from(activity).inflate(
                    R.layout.view_interview_turn, target, false);
            LinearLayout bubble = row.findViewById(R.id.interview_turn_bubble);
            View marker = row.findViewById(R.id.interview_turn_marker);
            TextView speaker = row.findViewById(R.id.interview_turn_speaker);
            TextView message = row.findViewById(R.id.interview_turn_message);

            InterviewDossier dossier = knownDossiers.get(id);
            String characterName = dossier == null
                    ? getString(R.string.chat_character) : dossier.getCharacterName();
            speaker.setText(fromPlayer
                    ? getString(R.string.interview_player_question) : characterName);
            speaker.setTextColor(fromPlayer ? 0xFFF2B84B : MINT);
            message.setText(turn.getText());
            speaker.setGravity(Gravity.START);
            message.setGravity(Gravity.START);
            bubble.setBackgroundResource(fromPlayer
                    ? R.drawable.interview_bubble_player
                    : R.drawable.interview_bubble_character);

            FrameLayout.LayoutParams bubbleParams =
                    (FrameLayout.LayoutParams) bubble.getLayoutParams();
            bubbleParams.gravity = Gravity.TOP | Gravity.START;
            bubbleParams.leftMargin = dp(14);
            bubbleParams.rightMargin = dp(4);
            bubble.setLayoutParams(bubbleParams);

            FrameLayout.LayoutParams markerParams =
                    (FrameLayout.LayoutParams) marker.getLayoutParams();
            markerParams.gravity = Gravity.TOP | Gravity.START;
            markerParams.leftMargin = dp(2);
            markerParams.rightMargin = 0;
            marker.setLayoutParams(markerParams);
            marker.setSelected(index == turns.size() - 1);
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            target.addView(row);
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private List<InterviewSender.ChatTurn> historyFor(String id) {
        List<InterviewSender.ChatTurn> history = histories.get(id);
        if (history == null) {
            history = new ArrayList<>();
            histories.put(id, history);
        }
        return history;
    }

    private void track(AlertDialog dialog) {
        activeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
        });
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(IVORY);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(int label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setBackgroundTintList(ColorStateList.valueOf(MINT));
        button.setMinHeight(dp(48));
        return button;
    }

    private void styleAlertDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(roundRect(PANEL, DECOR));
        int alertTitleId = activity.getResources().getIdentifier(
                "alertTitle", "id", "android");
        TextView title = alertTitleId == 0 ? null : dialog.findViewById(alertTitleId);
        if (title != null) {
            title.setTextColor(IVORY);
            title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(IVORY);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button action = dialog.getButton(which);
            if (action == null) continue;
            action.setTextColor(INK);
            action.setBackgroundTintList(ColorStateList.valueOf(MINT));
            action.setMinHeight(dp(48));
        }
    }

    private GradientDrawable roundRect(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0f);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String getString(int resource) { return activity.getString(resource); }

    private static final class PendingInterview {
        private final InterviewDossier dossier;
        private final List<InterviewSender.ChatTurn> historyBefore;
        private final String message;

        private PendingInterview(InterviewDossier dossier,
                                 List<InterviewSender.ChatTurn> historyBefore, String message) {
            this.dossier = dossier;
            this.historyBefore = historyBefore;
            this.message = message;
        }
    }
}
