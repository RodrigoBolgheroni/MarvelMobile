package com.projetos.marvel.caseengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Instantâneo imutável contendo somente dados que a sala pode exibir. */
public final class RoomViewState {
    private final PlayerView player;
    private final List<CharacterView> characters;
    private final List<ClueView> clues;
    private final Optional<AccusationResult> accusationResult;

    RoomViewState(PlayerView player, List<CharacterView> characters, List<ClueView> clues,
                  AccusationResult accusationResult) {
        this.player = player;
        this.characters = Collections.unmodifiableList(new ArrayList<>(characters));
        this.clues = Collections.unmodifiableList(new ArrayList<>(clues));
        this.accusationResult = Optional.ofNullable(accusationResult);
    }

    public PlayerView getPlayer() {
        return player;
    }

    public List<CharacterView> getCharacters() {
        return characters;
    }

    public List<ClueView> getClues() {
        return clues;
    }

    public Optional<AccusationResult> getAccusationResult() {
        return accusationResult;
    }
}