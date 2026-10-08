package com.projetos.marvel;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.projetos.marvel.ai.FirebaseInterviewSender;
import com.projetos.marvel.caseengine.AccusationResult;
import com.projetos.marvel.ui.room.InterviewSender;
import com.projetos.marvel.ui.room.RoomFlowController;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_GAMEPLAY_VISIBLE = "main.gameplay_visible";
    private static final String STATE_RESULT_VISIBLE = "main.result_visible";

    private RoomFlowController roomFlowController;
    private InterviewSender interviewSender;
    private Dialog menuInfoDialog;
    private boolean gameplayVisible;
    private boolean resultVisible;
    private boolean menuTransitioning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(false);
        insetsController.setAppearanceLightNavigationBars(false);

        interviewSender = new FirebaseInterviewSender();
        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof RoomFlowController) {
            roomFlowController = (RoomFlowController) retained;
            roomFlowController.setInterviewSender(interviewSender);
            roomFlowController.setResultListener(this::showResult);
            boolean restoreGameplay = savedInstanceState == null
                    || savedInstanceState.getBoolean(STATE_GAMEPLAY_VISIBLE, false);
            boolean restoreResult = savedInstanceState != null
                    && savedInstanceState.getBoolean(STATE_RESULT_VISIBLE, false);
            if (restoreResult && roomFlowController.getAccusationResult() != null) {
                showResult(roomFlowController.getAccusationResult());
            } else if (restoreGameplay) showGameplay(false);
            else showMenu();
        } else {
            showMenu();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (menuInfoDialog != null && menuInfoDialog.isShowing()) {
                    menuInfoDialog.dismiss();
                    return;
                }
                if (roomFlowController != null && roomFlowController.handleBack()) return;
                if (resultVisible) {
                    finishCaseAndShowMenu();
                    return;
                }
                if (gameplayVisible && roomFlowController != null) {
                    roomFlowController.retainForConfigurationChange();
                    showMenu();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void showMenu() {
        gameplayVisible = false;
        resultVisible = false;
        menuTransitioning = false;
        setContentView(R.layout.activity_menu);

        View menuContent = findViewById(R.id.menu_content);
        applySafeInsets(menuContent);
        ImageView backdrop = findViewById(R.id.menu_background);
        configurePixelBackdrop(backdrop);

        Button continueGame = findViewById(R.id.menu_continue);
        continueGame.setVisibility(roomFlowController == null ? View.GONE : View.VISIBLE);
        continueGame.setOnClickListener(view -> openGameplay(false));
        findViewById(R.id.menu_start).setOnClickListener(view -> openGameplay(true));
        findViewById(R.id.menu_how_to_play).setOnClickListener(
                view -> showMenuInfo(R.string.menu_how_to_play_title,
                        R.string.menu_how_to_play_body));
        findViewById(R.id.menu_credits).setOnClickListener(
                view -> showMenuInfo(R.string.menu_credits_title, R.string.menu_credits_body));
    }

    private void openGameplay(boolean newGame) {
        if (menuTransitioning) return;
        menuTransitioning = true;
        View menu = findViewById(R.id.menu_root);
        menu.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> showGameplay(newGame));
    }

    private void showGameplay(boolean newGame) {
        gameplayVisible = true;
        resultVisible = false;
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        applySafeInsets(root);
        if (roomFlowController == null) {
            roomFlowController = new RoomFlowController(this, root);
            roomFlowController.setInterviewSender(interviewSender);
            roomFlowController.setResultListener(this::showResult);
            roomFlowController.bind();
        } else {
            roomFlowController.attach(this, root);
            if (newGame) roomFlowController.startNewCase();
        }
    }

    private void showResult(AccusationResult result) {
        gameplayVisible = false;
        resultVisible = true;
        setContentView(R.layout.activity_result);
        applySafeInsets(findViewById(R.id.result_root));
        TextView title = findViewById(R.id.result_title);
        title.setText(result.isCorrect() ? R.string.result_victory : R.string.result_defeat);
        title.setTextColor(result.isCorrect() ? 0xFF44CFBD : 0xFFE4675D);
        TextView verdict = findViewById(R.id.result_verdict);
        verdict.setText(result.isCorrect() ? R.string.result_victory_body : R.string.result_defeat_body);
        ((TextView) findViewById(R.id.result_accused)).setText(
                getString(R.string.accused_label, result.getAccusedName()));
        ((TextView) findViewById(R.id.result_culprit)).setText(
                getString(R.string.result_culprit, result.getCulpritName()));
        ((TextView) findViewById(R.id.result_explanation)).setText(result.getExplanation());
        findViewById(R.id.result_menu).setOnClickListener(view -> finishCaseAndShowMenu());
    }

    private void finishCaseAndShowMenu() {
        if (roomFlowController != null) roomFlowController.setResultListener(null);
        roomFlowController = null;
        showMenu();
    }

    private void configurePixelBackdrop(ImageView image) {
        Drawable drawable = image.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            ((BitmapDrawable) drawable).setFilterBitmap(false);
        }
    }

    private void applySafeInsets(View view) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            target.setPadding(initialLeft + safeInsets.left, initialTop + safeInsets.top,
                    initialRight + safeInsets.right, initialBottom + safeInsets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    private void showMenuInfo(int titleResource, int bodyResource) {
        if (menuInfoDialog != null) menuInfoDialog.dismiss();
        Dialog dialog = new Dialog(this);
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_menu_info, null, false);
        TextView title = content.findViewById(R.id.menu_info_title);
        TextView body = content.findViewById(R.id.menu_info_body);
        Button close = content.findViewById(R.id.menu_info_close);
        title.setText(titleResource);
        body.setText(bodyResource);
        close.setOnClickListener(view -> dialog.dismiss());
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            if (menuInfoDialog == dialog) menuInfoDialog = null;
        });

        Window window = dialog.getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.72f);
        }
        menuInfoDialog = dialog;
        dialog.show();
        if (window != null) {
            int availableWidth = getResources().getDisplayMetrics().widthPixels;
            int availableHeight = getResources().getDisplayMetrics().heightPixels;
            int width = Math.min(dp(560), Math.max(dp(220), availableWidth - dp(32)));
            int height = Math.min(dp(520), Math.max(dp(280), availableHeight - dp(48)));
            window.setLayout(width, height);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_GAMEPLAY_VISIBLE, gameplayVisible);
        outState.putBoolean(STATE_RESULT_VISIBLE, resultVisible);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (menuInfoDialog != null) {
            menuInfoDialog.dismiss();
            menuInfoDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (isChangingConfigurations() && roomFlowController != null) {
            roomFlowController.retainForConfigurationChange();
            return roomFlowController;
        }
        return super.onRetainCustomNonConfigurationInstance();
    }

    /** Injection point for the real interview service when credentials are configured. */
    public void setInterviewSender(InterviewSender sender) {
        interviewSender = sender;
        if (roomFlowController != null) roomFlowController.setInterviewSender(sender);
    }
}
