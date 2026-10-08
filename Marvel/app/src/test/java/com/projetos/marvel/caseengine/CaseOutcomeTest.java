package com.projetos.marvel.caseengine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CaseOutcomeTest {
    @Test
    public void allTwentyCasesRevealTheCulpritOnlyAfterTheFinalAccusation() {
        Set<String> combinations = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            CaseSession session = CaseEngine.newSession(seed);
            RoomViewState room = session.getRoomViewState();
            assertFalse(room.getAccusationResult().isPresent());
            List<CharacterView> suspects = new ArrayList<>();
            String victim = null;
            for (CharacterView character : room.getCharacters()) {
                if (character.canAccuse()) suspects.add(character);
                else victim = character.getDisplayName();
            }
            assertEquals(4, suspects.size());
            int correctCount = 0;
            String culprit = null;
            for (CharacterView suspect : suspects) {
                AccusationResult result = CaseEngine.newSession(seed).accuse(suspect.getId());
                assertFalse(result.getCulpritName().isEmpty());
                if (result.isCorrect()) {
                    correctCount++;
                    culprit = suspect.getDisplayName();
                }
            }
            assertEquals(1, correctCount);
            AccusationResult result = session.accuse(suspects.get(0).getId());
            assertEquals(culprit, result.getCulpritName());
            assertEquals(result, session.getRoomViewState().getAccusationResult().get());
            combinations.add(victim + "/" + culprit);
            try {
                session.accuse(suspects.get(1).getId());
                fail("A finished case must reject a second accusation");
            } catch (IllegalStateException expected) {
                assertEquals(result, session.getRoomViewState().getAccusationResult().get());
            }
        }
        assertEquals(20, combinations.size());
    }
}
