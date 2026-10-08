package com.projetos.marvel.comicvine;

/** Public Comic Vine biography, separate from investigation facts. */
public final class CharacterProfile {
    private static final int MAX_SUMMARY = 1000;
    private final String characterId;
    private final int comicVineId;
    private final String name;
    private final String realName;
    private final String summary;
    private final String sourceUrl;

    CharacterProfile(String characterId, int comicVineId, String name, String realName,
                     String summary, String sourceUrl) {
        this.characterId = safe(characterId);
        this.comicVineId = comicVineId;
        this.name = safe(name);
        this.realName = safe(realName);
        String text = safe(summary);
        if (text.length() > MAX_SUMMARY) {
            int end = Character.isHighSurrogate(text.charAt(MAX_SUMMARY - 1))
                    ? MAX_SUMMARY - 1 : MAX_SUMMARY;
            text = text.substring(0, end).trim() + "…";
        }
        this.summary = text;
        this.sourceUrl = safe(sourceUrl);
    }

    public String getCharacterId() { return characterId; }
    public int getComicVineId() { return comicVineId; }
    public String getName() { return name; }
    public String getRealName() { return realName; }
    /** Plain text, limited to 1,000 characters. */
    public String getSummary() { return summary; }
    public String getSourceUrl() { return sourceUrl; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
