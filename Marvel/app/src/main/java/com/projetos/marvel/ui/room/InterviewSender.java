package com.projetos.marvel.ui.room;

import com.projetos.marvel.caseengine.InterviewDossier;
import java.util.List;

/** Replaceable boundary for the chosen survivor's dossier, delivered history, and new message. */
public interface InterviewSender {
    void send(InterviewDossier dossier, List<ChatTurn> history, String message, Callback callback);

    interface Callback {
        void onSuccess(String response);
        void onFailure(String message);
    }

    final class ChatTurn {
        private final boolean fromPlayer;
        private final String text;
        public ChatTurn(boolean fromPlayer, String text) {
            this.fromPlayer = fromPlayer;
            this.text = text;
        }
        public boolean isFromPlayer() { return fromPlayer; }
        public String getText() { return text; }
    }
}