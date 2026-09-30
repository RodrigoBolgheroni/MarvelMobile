package com.projetos.marvel.ai;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.ChatFutures;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.RequestOptions;
import com.projetos.marvel.caseengine.InterviewDossier;
import com.projetos.marvel.ui.room.InterviewSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase AI Logic adapter. Its only game inputs are the selected dossier,
 * that character's prior chat turns, and the new user message.
 */
public final class FirebaseInterviewSender implements InterviewSender {
    private static final String MODEL_NAME = "gemini-3.8-flash";
    private static final String RETRYABLE_FAILURE =
            "Falha ao consultar o Gemini. Verifique a conexao e tente novamente.";

    @Override
    public void send(InterviewDossier dossier, List<ChatTurn> history,
                     String message, Callback callback) {
        if (callback == null) {
            return;
        }
        if (dossier == null || message == null || message.trim().isEmpty()) {
            callback.onFailure("Dossie ou mensagem invalida.");
            return;
        }

        final ListenableFuture<GenerateContentResponse> response;
        try {
            Content systemInstruction = buildSystemInstruction(dossier);
            GenerativeModel rawModel = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel(
                            MODEL_NAME,
                            null,
                            null,
                            null,
                            null,
                            systemInstruction,
                            new RequestOptions(180_000L, 10));
            GenerativeModelFutures model = GenerativeModelFutures.from(rawModel);
            List<Content> sdkHistory = toSdkHistory(history);
            ChatFutures chat = sdkHistory.isEmpty()
                    ? model.startChat()
                    : model.startChat(sdkHistory);

            Content.Builder userMessage = new Content.Builder();
            userMessage.setRole("user");
            userMessage.addText(message);
            response = chat.sendMessage(userMessage.build());
        } catch (RuntimeException exception) {
            callback.onFailure(RETRYABLE_FAILURE);
            return;
        }

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result == null ? null : result.getText();
                if (text == null || text.trim().isEmpty()) {
                    callback.onFailure(RETRYABLE_FAILURE);
                    return;
                }
                callback.onSuccess(text.trim());
            }

            @Override
            public void onFailure(Throwable failure) {
                callback.onFailure(RETRYABLE_FAILURE);
            }
        }, MoreExecutors.directExecutor());
    }

    private static List<Content> toSdkHistory(List<ChatTurn> history) {
        List<Content> sdkHistory = new ArrayList<>();
        if (history == null) {
            return sdkHistory;
        }
        for (ChatTurn turn : history) {
            if (turn == null || turn.getText() == null || turn.getText().trim().isEmpty()) {
                continue;
            }
            Content.Builder item = new Content.Builder();
            item.setRole(turn.isFromPlayer() ? "user" : "model");
            item.addText(turn.getText());
            sdkHistory.add(item.build());
        }
        return sdkHistory;
    }

    private static Content buildSystemInstruction(InterviewDossier dossier) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are ")
                .append(dossier.getCharacterName())
                .append(" in a brief investigation interview. ")
                .append("Reply briefly in Brazilian Portuguese and keep this character's voice. ")
                .append("Use only the facts listed in this dossier for investigation answers. ")
                .append("When the dossier does not contain an answer, say you do not know. ")
                .append("The game engine determines decisive clues and the final verdict.\n")
                .append("Personality: ")
                .append(dossier.getPersonality())
                .append("\nKnown facts:\n");

        for (String fact : dossier.getKnownFacts()) {
            prompt.append("- ").append(fact).append("\n");
        }

        return new Content.Builder().addText(prompt.toString()).build();
    }
}