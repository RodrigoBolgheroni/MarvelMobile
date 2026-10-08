package com.projetos.marvel;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.projetos.marvel.ai.FirebaseInterviewSender;
import com.projetos.marvel.caseengine.AccusationResult;
import com.projetos.marvel.comicvine.CharacterProfile;
import com.projetos.marvel.comicvine.ComicVineProfiles;
import com.projetos.marvel.comicvine.ComicVineProfiles.Origin;
import com.projetos.marvel.ui.room.InterviewSender;
import com.projetos.marvel.ui.room.RoomFlowController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_GAMEPLAY_VISIBLE = "main.gameplay_visible";
    private static final String STATE_RESULT_VISIBLE = "main.result_visible";
    private static final String STATE_ROSTER_VISIBLE = "main.roster_visible";
    private static final String STATE_PROFILE_CHARACTER_ID = "main.profile_character_id";
    private static final String[] PROFILE_IDS = {
            "iron_man", "captain_america", "thor", "black_widow", "hawkeye"
    };
    private static final int[] PROFILE_NAME_RESOURCES = {
            R.string.comic_vine_character_iron_man,
            R.string.comic_vine_character_captain_america,
            R.string.comic_vine_character_thor,
            R.string.comic_vine_character_black_widow,
            R.string.comic_vine_character_hawkeye
    };
    private static final int[] PROFILE_ROLE_RESOURCES = {
            R.string.comic_vine_role_iron_man,
            R.string.comic_vine_role_captain_america,
            R.string.comic_vine_role_thor,
            R.string.comic_vine_role_black_widow,
            R.string.comic_vine_role_hawkeye
    };

    private RoomFlowController roomFlowController;
    private InterviewSender interviewSender;
    private ComicVineProfiles comicVineProfiles;
    private Map<String, CharacterProfile> characterProfiles = Collections.emptyMap();
    private Origin profileOrigin = Origin.NONE;
    private String profileError;
    private boolean profilesLoading;
    private Dialog menuInfoDialog;
    private Dialog rosterDialog;
    private View rosterDialogContent;
    private Dialog profileDialog;
    private View profileDialogContent;
    private String profileDialogCharacterId;
    private Bitmap[] profilePortraits;
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
        comicVineProfiles = new ComicVineProfiles(getApplicationContext());
        loadComicVineProfiles(false);
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

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(STATE_ROSTER_VISIBLE, false)) showRoster();
            String profileId = savedInstanceState.getString(STATE_PROFILE_CHARACTER_ID);
            if (profileId != null && profileIndex(profileId) >= 0) showProfile(profileId);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (profileDialog != null && profileDialog.isShowing()) {
                    profileDialog.dismiss();
                    return;
                }
                if (rosterDialog != null && rosterDialog.isShowing()) {
                    rosterDialog.dismiss();
                    return;
                }
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
        findViewById(R.id.menu_roster).setOnClickListener(view -> showRoster());
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
        roomFlowController.setCharacterProfiles(characterProfiles);
        roomFlowController.setProfileListener(this::showProfile);
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

    private void loadComicVineProfiles(boolean forceRefresh) {
        profilesLoading = true;
        profileError = null;
        renderRoster();
        renderProfileDetail();
        try {
            comicVineProfiles.load(forceRefresh, this::onProfilesLoaded);
        } catch (RuntimeException exception) {
            onProfilesLoaded(Collections.emptyMap(), Origin.NONE, null);
        }
    }

    private void onProfilesLoaded(Map<String, CharacterProfile> profiles,
                                  Origin origin, String errorMessage) {
        if (isFinishing() || isDestroyed()) return;
        profilesLoading = false;
        if ((origin == Origin.LIVE || origin == Origin.CACHE)
                && hasCompleteProfileSet(profiles)) {
            characterProfiles = Collections.unmodifiableMap(
                    new LinkedHashMap<>(profiles));
            profileOrigin = origin;
            profileError = safeProfileError(errorMessage);
        } else {
            characterProfiles = Collections.emptyMap();
            profileOrigin = Origin.NONE;
            profileError = safeProfileError(errorMessage);
        }
        injectProfilesIntoInterviewSender();
        if (roomFlowController != null) {
            roomFlowController.setCharacterProfiles(characterProfiles);
        }
        renderRoster();
        renderProfileDetail();
    }

    private boolean hasCompleteProfileSet(Map<String, CharacterProfile> profiles) {
        if (profiles == null || profiles.size() != PROFILE_IDS.length) return false;
        for (String id : PROFILE_IDS) {
            if (!profiles.containsKey(id) || profiles.get(id) == null) return false;
        }
        return true;
    }

    private String safeProfileError(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) return null;
        String safe = errorMessage.replaceAll("(?i)https?://\\S+", "[link removido]")
                .replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)\\S+", "$1[redigida]")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        return safe.length() > 160 ? safe.substring(0, 157) + "…" : safe;
    }

    private void injectProfilesIntoInterviewSender() {
        if (interviewSender instanceof FirebaseInterviewSender) {
            ((FirebaseInterviewSender) interviewSender).setProfiles(characterProfiles);
        }
    }

    private void showRoster() {
        if (rosterDialog != null && rosterDialog.isShowing()) {
            renderRoster();
            return;
        }
        Dialog dialog = new Dialog(this);
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_character_roster, null, false);
        Button close = content.findViewById(R.id.roster_close);
        Button retry = content.findViewById(R.id.roster_retry);
        close.setOnClickListener(view -> dialog.dismiss());
        retry.setOnClickListener(view -> loadComicVineProfiles(true));
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            if (rosterDialog == dialog) {
                rosterDialog = null;
                rosterDialogContent = null;
            }
        });
        prepareProfileDialogWindow(dialog);
        rosterDialog = dialog;
        rosterDialogContent = content;
        dialog.show();
        sizeProfileDialog(dialog);
        renderRoster();
    }

    private void renderRoster() {
        if (rosterDialog == null || !rosterDialog.isShowing()) return;
        View content = rosterDialogContent;
        if (content == null) return;
        TextView status = content.findViewById(R.id.roster_status);
        ProgressBar progress = content.findViewById(R.id.roster_progress);
        Button retry = content.findViewById(R.id.roster_retry);
        LinearLayout entries = content.findViewById(R.id.roster_profiles);
        progress.setVisibility(profilesLoading ? View.VISIBLE : View.GONE);
        if (profilesLoading) {
            status.setText(R.string.comic_vine_roster_loading);
        } else if (profileOrigin == Origin.LIVE) {
            status.setText(R.string.comic_vine_roster_live);
        } else if (profileOrigin == Origin.CACHE && profileError != null) {
            status.setText(getString(R.string.comic_vine_roster_cache_error, profileError));
        } else if (profileOrigin == Origin.CACHE) {
            status.setText(R.string.comic_vine_roster_cache);
        } else if (profileError != null) {
            status.setText(getString(R.string.comic_vine_roster_none_error, profileError));
        } else {
            status.setText(R.string.comic_vine_roster_none);
        }
        retry.setVisibility(!profilesLoading && profileOrigin != Origin.LIVE
                ? View.VISIBLE : View.GONE);
        retry.setEnabled(!profilesLoading);
        entries.removeAllViews();
        for (int index = 0; index < PROFILE_IDS.length; index++) {
            String id = PROFILE_IDS[index];
            CharacterProfile profile = characterProfiles.get(id);
            View row = LayoutInflater.from(this).inflate(
                    R.layout.view_roster_character, entries, false);
            String displayName = profile == null
                    ? getString(PROFILE_NAME_RESOURCES[index]) : profile.getName();
            String realName = profile == null || profile.getRealName() == null
                    || profile.getRealName().trim().isEmpty()
                    ? getString(PROFILE_ROLE_RESOURCES[index]) : profile.getRealName();
            TextView name = row.findViewById(R.id.roster_character_name);
            TextView identity = row.findViewById(R.id.roster_character_identity);
            TextView source = row.findViewById(R.id.roster_character_source);
            Button open = row.findViewById(R.id.roster_character_open);
            ImageView portrait = row.findViewById(R.id.roster_character_portrait);
            name.setText(displayName);
            identity.setText(realName);
            open.setContentDescription(getString(R.string.comic_vine_open_profile, displayName));
            portrait.setContentDescription(getString(
                    R.string.interview_portrait_description, displayName));
            bindProfilePortrait(portrait, id);
            if (profile != null) {
                source.setVisibility(View.VISIBLE);
                source.setContentDescription(getString(R.string.comic_vine_open_source));
                source.setOnClickListener(view -> openComicVineSource(profile.getSourceUrl()));
            } else {
                source.setVisibility(View.GONE);
                source.setOnClickListener(null);
            }
            open.setOnClickListener(view -> showProfile(id));
            entries.addView(row);
        }
    }

    private void showProfile(String characterId) {
        if (profileIndex(characterId) < 0) return;
        if (profileDialog != null && profileDialog.isShowing()
                && characterId.equals(profileDialogCharacterId)) {
            renderProfileDetail();
            return;
        }
        if (profileDialog != null) profileDialog.dismiss();
        Dialog dialog = new Dialog(this);
        View content = LayoutInflater.from(this).inflate(
                R.layout.dialog_character_profile, null, false);
        Button close = content.findViewById(R.id.profile_detail_close);
        Button retry = content.findViewById(R.id.profile_detail_retry);
        close.setOnClickListener(view -> dialog.dismiss());
        retry.setOnClickListener(view -> loadComicVineProfiles(true));
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            if (profileDialog == dialog) {
                profileDialog = null;
                profileDialogContent = null;
                profileDialogCharacterId = null;
            }
        });
        prepareProfileDialogWindow(dialog);
        profileDialog = dialog;
        profileDialogContent = content;
        profileDialogCharacterId = characterId;
        dialog.show();
        sizeProfileDialog(dialog);
        renderProfileDetail();
    }

    private void renderProfileDetail() {
        View content = profileDialogContent;
        String id = profileDialogCharacterId;
        if (content == null || id == null) return;
        int index = profileIndex(id);
        if (index < 0) return;
        CharacterProfile profile = characterProfiles.get(id);
        String name = profile == null
                ? getString(PROFILE_NAME_RESOURCES[index]) : profile.getName();
        String realName = profile == null || profile.getRealName() == null
                || profile.getRealName().trim().isEmpty()
                ? getString(PROFILE_ROLE_RESOURCES[index]) : profile.getRealName();
        ((TextView) content.findViewById(R.id.profile_detail_heading)).setText(name);
        ((TextView) content.findViewById(R.id.profile_detail_name)).setText(name);
        ((TextView) content.findViewById(R.id.profile_detail_real_name)).setText(realName);
        ImageView portrait = content.findViewById(R.id.profile_detail_portrait);
        portrait.setContentDescription(getString(R.string.interview_portrait_description, name));
        bindProfilePortrait(portrait, id);
        TextView status = content.findViewById(R.id.profile_detail_status);
        TextView summary = content.findViewById(R.id.profile_detail_summary);
        TextView source = content.findViewById(R.id.profile_detail_source);
        Button retry = content.findViewById(R.id.profile_detail_retry);
        if (profilesLoading) {
            status.setText(R.string.comic_vine_roster_loading);
            summary.setText(R.string.comic_vine_loading_profile);
        } else if (profile == null) {
            status.setText(profileError == null
                    ? getString(R.string.comic_vine_roster_none)
                    : getString(R.string.comic_vine_roster_none_error, profileError));
            summary.setText(R.string.comic_vine_profile_unavailable);
        } else {
            status.setText(profileOrigin == Origin.CACHE
                    ? R.string.comic_vine_profile_loaded_cache
                    : R.string.comic_vine_profile_loaded_live);
            summary.setText(profile.getSummary());
        }
        boolean hasSource = profile != null && isComicVineSource(profile.getSourceUrl());
        source.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        source.setOnClickListener(hasSource
                ? view -> openComicVineSource(profile.getSourceUrl()) : null);
        boolean canRetry = !profilesLoading && profileOrigin != Origin.LIVE;
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        retry.setEnabled(canRetry);
    }

    private void prepareProfileDialogWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, true);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.72f);
    }

    private void sizeProfileDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int availableWidth = getResources().getDisplayMetrics().widthPixels;
        int availableHeight = getResources().getDisplayMetrics().heightPixels;
        int width = Math.max(dp(240), Math.min(dp(720), availableWidth - dp(24)));
        int height = Math.max(dp(240), Math.min(dp(720), availableHeight - dp(48)));
        window.setLayout(width, height);
    }

    private void bindProfilePortrait(ImageView image, String characterId) {
        int frame = profileIndex(characterId);
        if (frame < 0) return;
        if (profilePortraits == null) {
            Bitmap atlas = BitmapFactory.decodeResource(
                    getResources(), R.drawable.survivor_idle_atlas_80x24);
            if (atlas == null || atlas.getWidth() < 80 || atlas.getHeight() < 24) return;
            profilePortraits = new Bitmap[PROFILE_IDS.length];
            for (int index = 0; index < PROFILE_IDS.length; index++) {
                profilePortraits[index] = Bitmap.createBitmap(atlas, index * 16, 0, 16, 24);
            }
            atlas.recycle();
        }
        Bitmap bitmap = profilePortraits[frame];
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setFilterBitmap(false);
        image.setImageDrawable(drawable);
    }

    private int profileIndex(String characterId) {
        if (characterId == null) return -1;
        for (int index = 0; index < PROFILE_IDS.length; index++) {
            if (PROFILE_IDS[index].equals(characterId)) return index;
        }
        return -1;
    }

    private boolean isComicVineSource(String sourceUrl) {
        if (sourceUrl == null) return false;
        Uri uri = Uri.parse(sourceUrl);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && ("comicvine.gamespot.com".equalsIgnoreCase(host)
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".comicvine.gamespot.com"));
    }

    private void openComicVineSource(String sourceUrl) {
        if (!isComicVineSource(sourceUrl)) {
            Toast.makeText(this, R.string.comic_vine_source_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)));
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, R.string.comic_vine_source_unavailable, Toast.LENGTH_SHORT).show();
        }
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
        outState.putBoolean(STATE_ROSTER_VISIBLE,
                rosterDialog != null && rosterDialog.isShowing());
        if (profileDialog != null && profileDialog.isShowing()
                && profileDialogCharacterId != null) {
            outState.putString(STATE_PROFILE_CHARACTER_ID, profileDialogCharacterId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (menuInfoDialog != null) {
            menuInfoDialog.dismiss();
            menuInfoDialog = null;
        }
        if (profileDialog != null) {
            profileDialog.dismiss();
            profileDialog = null;
        }
        if (rosterDialog != null) {
            rosterDialog.dismiss();
            rosterDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (isChangingConfigurations() && roomFlowController != null) {
            roomFlowController.retainForConfigurationChange();
            roomFlowController.setProfileListener(null);
            return roomFlowController;
        }
        return super.onRetainCustomNonConfigurationInstance();
    }

    /** Injection point for the real interview service when credentials are configured. */
    public void setInterviewSender(InterviewSender sender) {
        interviewSender = sender;
        injectProfilesIntoInterviewSender();
        if (roomFlowController != null) roomFlowController.setInterviewSender(sender);
    }
}
