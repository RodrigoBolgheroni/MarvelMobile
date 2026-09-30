package com.projetos.marvel.caseengine;

/** Dados exibíveis de uma pista e seu estado de exame. */
public final class ClueView {
    private final String id;
    private final String title;
    private final String description;
    private final float x;
    private final float y;
    private final boolean examined;

    ClueView(String id, String title, String description, float x, float y, boolean examined) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.x = x;
        this.y = y;
        this.examined = examined;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    /** Coordenada horizontal normalizada entre 0 e 1. */
    public float getX() {
        return x;
    }

    /** Coordenada vertical normalizada entre 0 e 1. */
    public float getY() {
        return y;
    }

    public boolean isExamined() {
        return examined;
    }
}