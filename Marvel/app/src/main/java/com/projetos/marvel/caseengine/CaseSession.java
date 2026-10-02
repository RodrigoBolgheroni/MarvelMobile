package com.projetos.marvel.caseengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Sessão isolada que guarda o caso e controla o que a sala pode observar. */
public final class CaseSession {
    private static final String UNEXAMINED_DESCRIPTION = "Ainda não examinada.";

    private static final CharacterDefinition[] ROSTER = {
        new CharacterDefinition("iron_man", "Homem de Ferro", 0.06944445f, 0.4722222f,
            "Confiante e sarcástico; costuma explicar situações como se fossem um cálculo."),
        new CharacterDefinition("captain_america", "Capitão América", 0.4027778f, 0.3611111f,
            "Disciplinado e ponderado, guiado por um forte senso de dever."),
        new CharacterDefinition("thor", "Thor", 0.18055555f, 0.8055556f,
            "Direto e cordial; demonstra orgulho e interpreta as palavras literalmente."),
        new CharacterDefinition("black_widow", "Viúva Negra", 0.625f, 0.30555555f,
            "Reservada e precisa, atenta a detalhes que outros deixam passar."),
        new CharacterDefinition("hawkeye", "Gavião Arqueiro", 0.7916667f, 0.8055556f,
            "Observador e pragmático; usa humor seco para aliviar a tensão.")
    };

    private static final String[][] TITLE_OPTIONS = {
        {"Log de acesso", "Registro do painel"},
        {"Lacre interno", "Marca na vedação"},
        {"Amostra do frasco", "Resíduo no recipiente"}
    };

    private static final String[][][] SIGNAL_LABELS = {
        {
            {"um pulso curto", "dois pulsos curtos"},
            {"um pulso longo", "dois pulsos longos"}
        },
        {
            {"fibra lisa", "fibra áspera"},
            {"fio prateado", "fio escuro"}
        },
        {
            {"reação azul", "reação violeta"},
            {"reação verde", "reação âmbar"}
        }
    };

    private static final int[][] UNIQUE_SIGNATURES = {
        {0, 0, 0},
        {0, 1, 1},
        {1, 0, 1},
        {1, 1, 0}
    };

    private final CaseFile caseFile;

    CaseSession(long seed) {
        this.caseFile = CaseFile.create(seed);
    }

    /**
     * Move o jogador para uma posição normalizada. Valores fora de [0, 1] ou
     * não finitos são rejeitados.
     */
    public void movePlayerTo(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f) {
            throw new IllegalArgumentException("As coordenadas devem ser finitas e estar entre 0 e 1.");
        }
        caseFile.playerX = x;
        caseFile.playerY = y;
    }

    public RoomViewState getRoomViewState() {
        List<CharacterView> characters = new ArrayList<>();
        for (CharacterData character : caseFile.characters) {
            boolean isVictim = character.definition.id.equals(caseFile.victimId);
            CharacterDefinition definition = character.definition;
            characters.add(new CharacterView(definition.id, definition.displayName,
                definition.x, definition.y, isVictim));
        }

        List<ClueView> clues = new ArrayList<>();
        for (ClueData clue : caseFile.clues) {
            clues.add(toView(clue));
        }

        return new RoomViewState(
            new PlayerView(caseFile.playerX, caseFile.playerY),
            characters,
            clues,
            caseFile.accusationResult
        );
    }

    /**
     * Examina uma pista próxima e marca seu conteúdo como descoberto.
     *
     * @throws IllegalArgumentException se clueId não identificar uma pista
     * @throws IllegalStateException se o jogador estiver longe da pista
     */
    public ClueView examine(String clueId) {
        ClueData clue = findClue(clueId);
        if (clue == null) {
            throw new IllegalArgumentException("Pista desconhecida: " + clueId);
        }
        if (!isNear(caseFile.playerX, caseFile.playerY, clue.x, clue.y)) {
            throw new IllegalStateException("O jogador está longe da pista.");
        }
        caseFile.examinedClueIds.add(clue.id);
        return toView(clue);
    }

    /**
     * Retorna somente o depoimento do sobrevivente solicitado e próximo.
     *
     * @throws IllegalArgumentException se survivorId não identificar um sobrevivente
     * @throws IllegalStateException se o jogador estiver longe do sobrevivente
     */
    public InterviewDossier getInterviewDossier(String survivorId) {
        InterviewDossier dossier = caseFile.dossiers.get(survivorId);
        if (dossier == null) {
            throw new IllegalArgumentException("Sobrevivente desconhecido: " + survivorId);
        }

        CharacterData character = findCharacter(survivorId);
        CharacterDefinition definition = character.definition;
        if (!isNear(caseFile.playerX, caseFile.playerY, definition.x, definition.y)) {
            throw new IllegalStateException("O jogador está longe do sobrevivente.");
        }
        return dossier;
    }

    /**
     * Avalia e registra a acusação mais recente. A vítima não pode ser acusada.
     *
     * @throws IllegalArgumentException se suspectId não identificar um sobrevivente
     */
    public AccusationResult accuse(String suspectId) {
        CharacterData suspect = findCharacter(suspectId);
        if (suspect == null || suspect.definition.id.equals(caseFile.victimId)) {
            throw new IllegalArgumentException("Acusação inválida: " + suspectId);
        }

        String accusedName = suspect.definition.displayName;
        boolean correct = suspect.definition.id.equals(caseFile.culpritId);
        String summary = correct ? "Acusação correta" : "Acusação incorreta";
        String explanation = correct
            ? "As três pistas identificam unicamente " + accusedName + "."
            : "Os sinais reunidos não correspondem ao padrão deste sobrevivente.";

        AccusationResult result = new AccusationResult(suspect.definition.id, accusedName, correct, summary, explanation);
        caseFile.accusationResult = result;
        return result;
    }

    private ClueView toView(ClueData clue) {
        boolean examined = caseFile.examinedClueIds.contains(clue.id);
        return new ClueView(
            clue.id,
            clue.title,
            examined ? clue.description : UNEXAMINED_DESCRIPTION,
            clue.x,
            clue.y,
            examined
        );
    }

    private ClueData findClue(String clueId) {
        for (ClueData clue : caseFile.clues) {
            if (clue.id.equals(clueId)) {
                return clue;
            }
        }
        return null;
    }

    private CharacterData findCharacter(String characterId) {
        if (characterId == null) {
            return null;
        }
        for (CharacterData character : caseFile.characters) {
            if (character.definition.id.equals(characterId)) {
                return character;
            }
        }
        return null;
    }

    private static boolean isNear(float fromX, float fromY, float toX, float toY) {
        return RoomInteractionRange.isWithinRange(fromX, fromY, toX, toY);
    }

    private static final class CaseFile {
        private final List<CharacterData> characters;
        private final String victimId;
        private final List<String> suspectIds;
        private final List<ClueData> clues;
        private final Map<String, InterviewDossier> dossiers;
        private final Map<String, int[]> signatures;
        private final String culpritId;
        private final Set<String> examinedClueIds = new HashSet<>();
        private float playerX = 0.50f;
        private float playerY = 0.90f;
        private AccusationResult accusationResult;

        private CaseFile(List<CharacterData> characters, String victimId, List<String> suspectIds,
                         List<ClueData> clues, Map<String, InterviewDossier> dossiers,
                         Map<String, int[]> signatures, String culpritId) {
            this.characters = Collections.unmodifiableList(new ArrayList<>(characters));
            this.victimId = victimId;
            this.suspectIds = Collections.unmodifiableList(new ArrayList<>(suspectIds));
            this.clues = Collections.unmodifiableList(new ArrayList<>(clues));
            this.dossiers = Collections.unmodifiableMap(new LinkedHashMap<>(dossiers));
            this.signatures = Collections.unmodifiableMap(new LinkedHashMap<>(signatures));
            this.culpritId = culpritId;
            verifyUniqueSolution();
        }

        private static CaseFile create(long seed) {
            int pairIndex = (int) Math.floorMod(seed, 20L);
            CharacterDefinition victim = ROSTER[pairIndex / 4];
            List<CharacterDefinition> survivors = new ArrayList<>();
            for (CharacterDefinition character : ROSTER) {
                if (!character.id.equals(victim.id)) {
                    survivors.add(character);
                }
            }
            String culpritId = survivors.get(pairIndex % 4).id;

            SplittableRandom random = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L);
            List<int[]> codewords = new ArrayList<>();
            for (int[] signature : UNIQUE_SIGNATURES) {
                codewords.add(signature.clone());
            }
            for (int index = codewords.size() - 1; index > 0; index--) {
                int swapIndex = random.nextInt(index + 1);
                Collections.swap(codewords, index, swapIndex);
            }

            Map<String, int[]> signatures = new LinkedHashMap<>();
            for (int index = 0; index < survivors.size(); index++) {
                signatures.put(survivors.get(index).id, codewords.get(index));
            }

            List<ClueData> clues = new ArrayList<>();
            String[] clueIds = {"clue_access_log", "clue_seal_trace", "clue_reagent_sample"};
            float[][] cluePositions = {{0.30f, 0.47f}, {0.50f, 0.389f}, {0.70f, 0.47f}};
            int[] culpritSignature = signatures.get(culpritId);
            String[][] chosenLabels = new String[3][];
            for (int index = 0; index < 3; index++) {
                int variant = random.nextInt(2);
                chosenLabels[index] = SIGNAL_LABELS[index][variant];
                String title = TITLE_OPTIONS[index][random.nextInt(TITLE_OPTIONS[index].length)];
                int hour = 21 + random.nextInt(3);
                int minute = random.nextInt(60);
                String time = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
                int signal = culpritSignature[index];
                String description = "Às " + time + ", " + title.toLowerCase(Locale.ROOT)
                    + " registrou o sinal “" + chosenLabels[index][signal]
                    + "”. O registro foi preservado durante o isolamento da sala.";
                clues.add(new ClueData(clueIds[index], title, description,
                    cluePositions[index][0], cluePositions[index][1], signal));
            }

            Map<String, InterviewDossier> dossiers = new LinkedHashMap<>();
            for (CharacterDefinition survivor : survivors) {
                int[] signature = signatures.get(survivor.id);
                List<String> facts = new ArrayList<>();
                for (int index = 0; index < clues.size(); index++) {
                    String title = clues.get(index).title.toLowerCase(Locale.ROOT);
                    facts.add("No " + title + ", minha assinatura individual corresponde ao sinal “"
                        + chosenLabels[index][signature[index]] + "”.");
                }
                dossiers.put(survivor.id, new InterviewDossier(
                    survivor.id, survivor.displayName, survivor.personality, facts));
            }

            List<CharacterData> characters = new ArrayList<>();
            for (CharacterDefinition definition : ROSTER) {
                characters.add(new CharacterData(definition));
            }

            List<String> suspectIds = new ArrayList<>();
            for (CharacterDefinition survivor : survivors) {
                suspectIds.add(survivor.id);
            }

            return new CaseFile(characters, victim.id, suspectIds, clues, dossiers, signatures, culpritId);
        }

        private void verifyUniqueSolution() {
            if (suspectIds.size() != 4 || clues.size() != 3 || signatures.size() != 4) {
                throw new IllegalStateException("O caso precisa de quatro suspeitos, três pistas e quatro assinaturas.");
            }
            int matchingSuspects = 0;
            String uniqueMatch = null;
            for (String suspectId : suspectIds) {
                int[] signature = signatures.get(suspectId);
                boolean matchesAllClues = true;
                for (int index = 0; index < clues.size(); index++) {
                    if (signature[index] != clues.get(index).signalIndex) {
                        matchesAllClues = false;
                        break;
                    }
                }
                if (matchesAllClues) {
                    matchingSuspects++;
                    uniqueMatch = suspectId;
                }
            }
            if (matchingSuspects != 1 || !culpritId.equals(uniqueMatch)) {
                throw new IllegalStateException("As três pistas não identificam exatamente um culpado.");
            }
        }
    }

    private static final class CharacterDefinition {
        private final String id;
        private final String displayName;
        private final float x;
        private final float y;
        private final String personality;

        private CharacterDefinition(String id, String displayName, float x, float y, String personality) {
            this.id = id;
            this.displayName = displayName;
            this.x = x;
            this.y = y;
            this.personality = personality;
        }
    }

    private static final class CharacterData {
        private final CharacterDefinition definition;

        private CharacterData(CharacterDefinition definition) {
            this.definition = definition;
        }
    }

    private static final class ClueData {
        private final String id;
        private final String title;
        private final String description;
        private final float x;
        private final float y;
        private final int signalIndex;

        private ClueData(String id, String title, String description, float x, float y, int signalIndex) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.x = x;
            this.y = y;
            this.signalIndex = signalIndex;
        }
    }
}
