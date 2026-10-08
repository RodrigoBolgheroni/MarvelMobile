package com.projetos.marvel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.projetos.marvel.ai.FirebaseInterviewSender;
import com.projetos.marvel.caseengine.CaseSession;
import com.projetos.marvel.caseengine.CharacterView;
import com.projetos.marvel.caseengine.ClueView;
import com.projetos.marvel.comicvine.CharacterProfile;
import com.projetos.marvel.ui.room.RoomFlowController;

import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the live Comic Vine response through the installed Android app. */
@RunWith(AndroidJUnit4.class)
public class ComicVineDeviceTest {
    @Test public void liveProfilesReachRosterAndInterviewPersona() throws Exception {
        Assume.assumeTrue("A Comic Vine key is required for the live device test.",
                !BuildConfig.COMIC_VINE_API_KEY.isEmpty());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.menu_roster).performClick());

        AtomicReference<String> status = new AtomicReference<>("");
        AtomicReference<LinearLayout> roster = new AtomicReference<>();
        AtomicBoolean retried = new AtomicBoolean(false);
        long deadline = System.currentTimeMillis() + 120_000L;
        do {
            instrumentation.runOnMainSync(() -> {
                Dialog dialog = (Dialog) field(activity, "rosterDialog");
                status.set(((TextView) dialog.findViewById(R.id.roster_status))
                        .getText().toString());
                roster.set(dialog.findViewById(R.id.roster_profiles));
                View retry = dialog.findViewById(R.id.roster_retry);
                if (!retried.get() && retry.getVisibility() == View.VISIBLE) {
                    retry.performClick();
                    retried.set(true);
                }
            });
            if (status.get().equals(activity.getString(R.string.comic_vine_roster_live))) break;
            Thread.sleep(500L);
        } while (System.currentTimeMillis() < deadline);

        assertEquals("Live Comic Vine status: " + status.get(),
                activity.getString(R.string.comic_vine_roster_live), status.get());
        assertEquals(5, roster.get().getChildCount());
        String[] expectedNames = {"Iron Man", "Captain America", "Thor", "Black Widow", "Hawkeye"};
        for (int i = 0; i < expectedNames.length; i++) {
            View row = roster.get().getChildAt(i);
            assertEquals(expectedNames[i], ((TextView) row.findViewById(
                    R.id.roster_character_name)).getText().toString());
            assertTrue(((TextView) row.findViewById(R.id.roster_character_identity))
                    .getText().length() > 3);
            assertEquals(View.VISIBLE, row.findViewById(R.id.roster_character_source)
                    .getVisibility());
        }

        @SuppressWarnings("unchecked")
        Map<String, CharacterProfile> profiles =
                (Map<String, CharacterProfile>) field(activity, "characterProfiles");
        assertEquals(5, profiles.size());
        assertTrue(profiles.get("iron_man").getSummary().length() > 30);
        assertTrue(profiles.get("iron_man").getSourceUrl()
                .startsWith("https://comicvine.gamespot.com/"));
        FirebaseInterviewSender sender =
                (FirebaseInterviewSender) field(activity, "interviewSender");
        @SuppressWarnings("unchecked")
        Map<String, CharacterProfile> personaProfiles =
                (Map<String, CharacterProfile>) field(sender, "profiles");
        assertNotNull(personaProfiles.get("iron_man"));
        assertEquals(profiles.get("iron_man").getSummary(),
                personaProfiles.get("iron_man").getSummary());

        instrumentation.runOnMainSync(activity::finish);
    }

    @Test public void investigationCanReachInterviewClueAndVerdictOnDevice()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.menu_start).performClick());
        long deadline = System.currentTimeMillis() + 10_000L;
        while (activity.findViewById(R.id.room_canvas) == null
                && System.currentTimeMillis() < deadline) Thread.sleep(100L);
        assertNotNull("Gameplay opened from the menu", activity.findViewById(R.id.room_canvas));

        RoomFlowController controller = (RoomFlowController) field(activity, "roomFlowController");
        CaseSession session = (CaseSession) field(controller, "session");
        assertNotNull(session);
        float startingX = session.getRoomViewState().getPlayer().getX();
        instrumentation.runOnMainSync(() -> walk(controller, 0.20f, 0.77f));
        long walkDeadline = System.currentTimeMillis() + 5_000L;
        while (Math.abs(session.getRoomViewState().getPlayer().getX() - startingX) < 0.01f
                && System.currentTimeMillis() < walkDeadline) Thread.sleep(100L);
        assertTrue("Room movement advanced the player",
                Math.abs(session.getRoomViewState().getPlayer().getX() - startingX) >= 0.01f);
        CharacterView survivor = null;
        for (CharacterView character : session.getRoomViewState().getCharacters()) {
            if (character.canInterview()) { survivor = character; break; }
        }
        assertNotNull(survivor);
        CharacterView selectedSurvivor = survivor;
        instrumentation.runOnMainSync(() -> {
            activity.setInterviewSender((dossier, history, message, callback) ->
                    callback.onSuccess("Depoimento de teste."));
            session.movePlayerTo(selectedSurvivor.getX(), selectedSurvivor.getY());
            invoke(controller, "handleCharacterTap", CharacterView.class, selectedSurvivor);
        });
        Dialog interview = (Dialog) field(controller, "activeDialog");
        assertNotNull(interview);
        instrumentation.runOnMainSync(() -> {
            ((EditText) interview.findViewById(R.id.interview_input))
                    .setText("O que você sabe?");
            interview.findViewById(R.id.interview_send).performClick();
        });
        LinearLayout transcript = interview.findViewById(R.id.interview_transcript);
        assertTrue("Question and answer entered the transcript",
                transcript.getChildCount() >= 2);
        instrumentation.runOnMainSync(interview::dismiss);

        ClueView clue = session.getRoomViewState().getClues().get(0);
        instrumentation.runOnMainSync(() -> {
            session.movePlayerTo(clue.getX(), clue.getY());
            invoke(controller, "handleClueTap", ClueView.class, clue);
        });
        assertTrue(session.getRoomViewState().getClues().get(0).isExamined());
        Dialog clueDialog = (Dialog) field(controller, "activeDialog");
        instrumentation.runOnMainSync(clueDialog::dismiss);

        instrumentation.runOnMainSync(() ->
                activity.findViewById(R.id.btn_accusation_slot).performClick());
        AlertDialog accusation = (AlertDialog) field(controller, "activeAccusationChoices");
        assertNotNull(accusation);
        instrumentation.runOnMainSync(() -> {
            accusation.getListView().performItemClick(null, 0, 0L);
            accusation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        assertNotNull(controller.getAccusationResult());
        assertNotNull(activity.findViewById(R.id.result_title));
        assertTrue(((TextView) activity.findViewById(R.id.result_culprit))
                .getText().length() > 3);
        instrumentation.runOnMainSync(() ->
                activity.findViewById(R.id.result_menu).performClick());
        assertNotNull(activity.findViewById(R.id.menu_start));
        instrumentation.runOnMainSync(activity::finish);
    }

    private static void invoke(Object owner, String name, Class<?> argumentType,
                               Object argument) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name, argumentType);
            method.setAccessible(true);
            method.invoke(owner, argument);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void walk(RoomFlowController controller, float x, float y) {
        try {
            Method method = controller.getClass().getDeclaredMethod(
                    "walkToNormalized", float.class, float.class);
            method.setAccessible(true);
            method.invoke(controller, x, y);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
