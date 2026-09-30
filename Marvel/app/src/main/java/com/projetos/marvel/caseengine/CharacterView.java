package com.projetos.marvel.caseengine;

/** Dados de personagem que a sala pode exibir e usar para habilitar ações. */
public final class CharacterView {
    private final String id;
    private final String displayName;
    private final float x;
    private final float y;
    private final boolean victim;

    CharacterView(String id, String displayName, float x, float y, boolean victim) {
        this.id = id;
        this.displayName = displayName;
        this.x = x;
        this.y = y;
        this.victim = victim;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Coordenada horizontal normalizada entre 0 e 1. */
    public float getX() {
        return x;
    }

    /** Coordenada vertical normalizada entre 0 e 1. */
    public float getY() {
        return y;
    }

    public boolean isVictim() {
        return victim;
    }

    public boolean canInterview() {
        return !victim;
    }

    public boolean canAccuse() {
        return !victim;
    }
}