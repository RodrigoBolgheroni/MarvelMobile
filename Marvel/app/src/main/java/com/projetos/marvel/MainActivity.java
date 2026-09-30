package com.projetos.marvel;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.projetos.marvel.ui.room.InterviewSender;
import com.projetos.marvel.ui.room.RoomFlowController;

public class MainActivity extends AppCompatActivity {
    private RoomFlowController roomFlowController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_main);
        roomFlowController = new RoomFlowController(this, findViewById(R.id.main));
        roomFlowController.bind();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!roomFlowController.handleBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    /** Injection point for the real interview service when credentials are configured. */
    public void setInterviewSender(InterviewSender sender) {
        if (roomFlowController != null) roomFlowController.setInterviewSender(sender);
    }
}
