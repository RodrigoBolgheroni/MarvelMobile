package com.projetos.marvel.caseengine;

/** Posição do jogador em coordenadas normalizadas entre 0 e 1. */
public final class PlayerView {
    private final float x;
    private final float y;

    PlayerView(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}