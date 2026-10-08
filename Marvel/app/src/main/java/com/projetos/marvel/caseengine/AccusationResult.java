package com.projetos.marvel.caseengine;

/** Veredito exibível após a acusação, incluindo a identidade revelada do culpado. */
public final class AccusationResult {
    private final String accusedId;
    private final String accusedName;
    private final String culpritName;
    private final boolean correct;
    private final String summary;
    private final String explanation;

    AccusationResult(String accusedId, String accusedName, String culpritName,
                     boolean correct, String summary, String explanation) {
        this.accusedId = accusedId;
        this.accusedName = accusedName;
        this.culpritName = culpritName;
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

    public String getCulpritName() {
        return culpritName;
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
