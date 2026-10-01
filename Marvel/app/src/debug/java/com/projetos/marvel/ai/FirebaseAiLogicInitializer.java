package com.projetos.marvel.ai;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/** Installs the debug App Check provider before the app uses Firebase AI Logic. */
public final class FirebaseAiLogicInitializer {
    private FirebaseAiLogicInitializer() {
    }

    public static void initialize(Context context) {
        Context appContext = context.getApplicationContext();
        FirebaseApp.initializeApp(appContext);
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
    }
}