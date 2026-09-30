package com.projetos.marvel.caseengine;

/** Resultado exibível de uma acusação. Não expõe a identidade oculta do culpado. */
public final class AccusationResult {
    private final String accusedId;
    private final String accusedName;
    private final boolean correct;
    private final String summary;
    private final String explanation;

    AccusationResult(String accusedId, String accusedName, boolean correct, String summary, String explanation) {
        this.accusedId = accusedId;
        this.accusedName = accusedName;
        this.correct = correct;
        this.summary = summary;
        this.explanation = explanation;
    }

    public String getAccusedId() {
        return accusedId;
    }

    public String getAccusedName() {
        return accusedName;
    }

    public boolean isCorrect() {
        return correct;
    }

    public String getSummary() {
        return summary;
    }

    public String getExplanation() {
        return explanation;
    }
}