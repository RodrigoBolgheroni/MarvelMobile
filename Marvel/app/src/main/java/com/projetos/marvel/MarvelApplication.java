package com.projetos.marvel;

import android.app.Application;

import com.projetos.marvel.ai.FirebaseAiLogicInitializer;

/** Initializes Firebase and the build-specific App Check provider before activities use AI Logic. */
public final class MarvelApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseAiLogicInitializer.initialize(this);
    }
}
