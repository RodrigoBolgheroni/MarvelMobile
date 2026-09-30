package com.projetos.marvel.ui.room;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RoomFlowController {
    private static final float INTERACTION_RADIUS = 0.12f;

    private final Activity activity;
    private final View root;
    private final Random random = new Random();
    private final Map<String, List<InterviewSender.ChatTurn>> histories = new HashMap<>();
    private final Map<String, InterviewDossier> knownDossiers = new HashMap<>();
    private final Map<String, PendingInterview> pending = new HashMap<>();
    private FrameLayout stage;
    private RoomCanvasView canvas;
    private TextView caseLabel;
    private TextView statusLabel;
    private TextView clueSummary;
    private Button accuseButton;
    private CaseSession session;
    private int seed = -1;
    private boolean resultShown;
    private Dialog activeDialog;
    private InterviewSender interviewSender;

    public RoomFlowController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
    }

    public void bind() {
        stage = root.findViewById(R.id.room_stage_container);
        caseLabel = root.findViewById(R.id.room_case_label);
        statusLabel = root.findViewById(R.id.room_status);
        clueSummary = root.findViewById(R.id.room_clue_summary);
        accuseButton = root.findViewById(R.id.btn_accuse);
        Button newCase = root.findViewById(R.id.btn_new_case);
        Button notebook = root.findViewById(R.id.btn_notebook);

        canvas = new RoomCanvasView(activity);
        stage.addView(canvas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        canvas.setListener(new RoomCanvasView.Listener() {
            @Override public void onFloorTapped(float x, float y) {
                session.movePlayerTo(x, y);
                render(getString(R.string.walked_to_position));
            }
            @Override public void onCharacterTapped(CharacterView character) {
                handleCharacterTap(character);
            }
            @Override public void onClueTapped(ClueView clue) {
                handleClueTap(clue);
            }
        });
        newCase.setOnClickListener(v -> startNewCase());
        notebook.setOnClickListener(v -> showNotebook());
        accuseButton.setOnClickListener(v -> showAccusationChoices());
        startNewCase();
    }

    public void setInterviewSender(InterviewSender sender) {
        interviewSender = sender;
    }

    public boolean handleBack() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            return true;
        }
        return false;
    }

    private void startNewCase() {
        int nextSeed = random.nextInt(20);
        if (seed >= 0 && nextSeed == seed) nextSeed = (nextSeed + 1) % 20;
        seed = nextSeed;
        resultShown = false;
        session = CaseEngine.newSession(seed);
        histories.clear();
        knownDossiers.clear();
        pending.clear();
        render(getString(R.string.room_ready));
    }

    private void render(String status) {
        RoomViewState state = session.getRoomViewState();
        canvas.render(state);
        caseLabel.setText(activity.getString(R.string.case_seed, seed));
        statusLabel.setText(status);
        StringBuilder summary = new StringBuilder();
        for (ClueView clue : state.getClues()) {
            if (summary.length() > 0) summary.append("\n\n");
            summary.append(clue.getTitle()).append("\n")
                    .append(clue.isExamined()
                            ? clue.getDescription() : getString(R.string.clue_not_examined));
        }
        clueSummary.setText(summary.toString());
        boolean finished = state.getAccusationResult().isPresent();
        accuseButton.setEnabled(!finished);
        state.getAccusationResult().ifPresent(result -> {
            if (!resultShown) {
                resultShown = true;
                showAccusationResult(result);
            }
        });
    }

    private void handleCharacterTap(CharacterView character) {
        if (!character.canInterview()) {
            statusLabel.setText(R.string.victim_cannot_interview);
            return;
        }
        if (!isNear(character.getX(), character.getY())) {
            session.movePlayerTo(character.getX(), character.getY());
            render(activity.getString(R.string.walked_to_character, character.getDisplayName()));
            return;
        }
        try {
            InterviewDossier dossier = session.getInterviewDossier(character.getId());
            knownDossiers.put(character.getId(), dossier);
            showInterview(dossier);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    private void handleClueTap(ClueView clue) {
        if (!isNear(clue.getX(), clue.getY())) {
            session.movePlayerTo(clue.getX(), clue.getY());
            render(activity.getString(R.string.walked_to_clue, clue.getTitle()));
            return;
        }
        try {
            ClueView examined = session.examine(clue.getId());
            render(activity.getString(R.string.clue_examined, examined.getTitle()));
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(examined.getTitle())
                    .setMessage(examined.getDescription())
                    .setPositiveButton(R.string.close, null)
                    .show();
            track(dialog);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    private boolean isNear(float x, float y) {
        RoomViewState state = session.getRoomViewState();
        float dx = state.getPlayer().getX() - x;
        float dy = state.getPlayer().getY() - y;
        return dx * dx + dy * dy <= INTERACTION_RADIUS * INTERACTION_RADIUS;
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
        notes.append("\n\n").append(getString(R.string.interview_facts_title));
        if (knownDossiers.isEmpty()) notes.append("\n").append(getString(R.string.no_interviews_yet));
        for (InterviewDossier dossier : knownDossiers.values()) {
            notes.append("\n\n").append(dossier.getCharacterName());
            for (String fact : dossier.getKnownFacts()) notes.append("\n• ").append(fact);
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.case_notebook)
                .setMessage(notes.toString())
                .setPositiveButton(R.string.close, null)
                .show();
        track(dialog);
    }

    private void showAccusationChoices() {
        List<CharacterView> suspects = new ArrayList<>();
        for (CharacterView character : session.getRoomViewState().getCharacters()) {
            if (character.canAccuse()) suspects.add(character);
        }
        if (suspects.isEmpty()) return;
        String[] names = new String[suspects.size()];
        for (int i = 0; i < suspects.size(); i++) names[i] = suspects.get(i).getDisplayName();
        int[] selected = {-1};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.choose_suspect)
                .setSingleChoiceItems(names, -1, (d, which) -> selected[0] = which)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.accuse, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (selected[0] < 0) {
                        statusLabel.setText(R.string.choose_suspect_first);
                        return;
                    }
                    AccusationResult result = session.accuse(suspects.get(selected[0]).getId());
                    dialog.dismiss();
                    render(result.getSummary());
                }));
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.show();
        activeDialog = dialog;
    }

    private void showAccusationResult(AccusationResult result) {
        String message = result.getSummary() + "\n\n"
                + activity.getString(R.string.accused_label, result.getAccusedName())
                + "\n\n" + result.getExplanation();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(result.isCorrect() ? R.string.verdict_correct : R.string.verdict_incorrect)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show();
        track(dialog);
    }

    private void showInterview(InterviewDossier dossier) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(16));
        panel.setBackground(roundRect(0xFF12232E, 0xFF355461));

        TextView heading = text(dossier.getCharacterName(), 20, true);
        panel.addView(heading, matchWrap());
        TextView personality = text(dossier.getPersonality(), 14, false);
        personality.setPadding(0, dp(6), 0, dp(8));
        panel.addView(personality, matchWrap());

        StringBuilder factText = new StringBuilder(getString(R.string.known_facts_title));
        for (String fact : dossier.getKnownFacts()) factText.append("\n• ").append(fact);
        panel.addView(text(factText.toString(), 13, false), matchWrap());

        ScrollView scroll = new ScrollView(activity);
        TextView transcript = text("", 14, false);
        transcript.setPadding(dp(10), dp(8), dp(10), dp(8));
        transcript.setBackground(roundRect(0xFF0B1720, 0xFF29424E));
        scroll.addView(transcript, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(12);
        scrollParams.bottomMargin = dp(8);
        panel.addView(scroll, scrollParams);

        TextView requestStatus = text("", 12, false);
        panel.addView(requestStatus, matchWrap());
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.chat_input_hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF9CB0BA);
        input.setBackground(roundRect(0xFF0B1720, 0xFF355461));
        Button send = button(R.string.send_message);
        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));
        inputRow.addView(send, new LinearLayout.LayoutParams(dp(116), dp(48)));
        panel.addView(inputRow, matchWrap());

        Button retry = button(R.string.retry);
        LinearLayout.LayoutParams retryParams = matchWrap();
        retryParams.topMargin = dp(5);
        panel.addView(retry, retryParams);
        Button close = button(R.string.close);
        panel.addView(close, matchWrap());

        String id = dossier.getCharacterId();
        renderTranscript(id, transcript);
        retry.setVisibility(pending.containsKey(id) ? View.VISIBLE : View.GONE);
        if (pending.containsKey(id)) requestStatus.setText(R.string.interview_pending_retry);
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            if (pending.containsKey(id)) {
                requestStatus.setText(R.string.retry_before_sending);
                retry.setVisibility(View.VISIBLE);
                return;
            }
            List<InterviewSender.ChatTurn> history = historyFor(id);
            List<InterviewSender.ChatTurn> oldHistory = new ArrayList<>(history);
            history.add(new InterviewSender.ChatTurn(true, message));
            pending.put(id, new PendingInterview(dossier, oldHistory, message));
            input.setText("");
            renderTranscript(id, transcript);
            retry.setVisibility(View.VISIBLE);
            sendPending(id, requestStatus, retry, transcript);
        });
        retry.setOnClickListener(v -> sendPending(id, requestStatus, retry, transcript));
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(panel);
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) activeDialog = null;
        });
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(32), dp(760));
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.88f);
            window.setLayout(width, height);
        }
        activeDialog = dialog;
    }

    private void sendPending(String id, TextView status, Button retry, TextView transcript) {
        PendingInterview request = pending.get(id);
        if (request == null) return;
        if (interviewSender == null) {
            status.setText(R.string.interview_service_unavailable);
            retry.setVisibility(View.VISIBLE);
            return;
        }
        status.setText(R.string.sending_interview);
        retry.setEnabled(false);
        try {
            interviewSender.send(request.dossier,
                    Collections.unmodifiableList(new ArrayList<>(request.historyBefore)),
                    request.message, new InterviewSender.Callback() {
                        @Override public void onSuccess(String response) {
                            activity.runOnUiThread(() -> {
                                historyFor(id).add(new InterviewSender.ChatTurn(false, response));
                                pending.remove(id);
                                renderTranscript(id, transcript);
                                status.setText(R.string.interview_received);
                                retry.setVisibility(View.GONE);
                            });
                        }
                        @Override public void onFailure(String message) {
                            activity.runOnUiThread(() -> {
                                status.setText(activity.getString(
                                        R.string.interview_failed_preserved, message));
                                retry.setEnabled(true);
                                retry.setVisibility(View.VISIBLE);
                            });
                        }
                    });
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? getString(R.string.unknown_error) : exception.getMessage();
            status.setText(activity.getString(R.string.interview_failed_preserved, message));
            retry.setEnabled(true);
            retry.setVisibility(View.VISIBLE);
        }
    }

    private void renderTranscript(String id, TextView target) {
        StringBuilder result = new StringBuilder();
        for (InterviewSender.ChatTurn turn : historyFor(id)) {
            result.append(getString(turn.isFromPlayer()
                    ? R.string.chat_you : R.string.chat_character))
                    .append(": ").append(turn.getText()).append("\n\n");
        }
        target.setText(result.length() == 0 ? getString(R.string.chat_empty) : result.toString());
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
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(int label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private GradientDrawable roundRect(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
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
