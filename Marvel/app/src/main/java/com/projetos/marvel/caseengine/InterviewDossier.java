package com.projetos.marvel.caseengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Personalidade e fatos liberados para uma única entrevista. */
public final class InterviewDossier {
    private final String characterId;
    private final String characterName;
    private final String personality;
    private final List<String> knownFacts;

    InterviewDossier(String characterId, String characterName, String personality, List<String> knownFacts) {
        this.characterId = characterId;
        this.characterName = characterName;
        this.personality = personality;
        this.knownFacts = Collections.unmodifiableList(new ArrayList<>(knownFacts));
    }

    public String getCharacterId() {
        return characterId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getPersonality() {
        return personality;
    }

    public List<String> getKnownFacts() {
        return knownFacts;
    }
}