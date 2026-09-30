package com.projetos.marvel.caseengine;

/** Cria sessões reproduzíveis do motor de investigação. */
public final class CaseEngine {
    private CaseEngine() {
    }

    /**
     * Cria uma partida reproduzível. As sementes de 0 a 19 cobrem uma vez cada
     * combinação possível de vítima e culpado.
     */
    public static CaseSession newSession(long seed) {
        return new CaseSession(seed);
    }
}