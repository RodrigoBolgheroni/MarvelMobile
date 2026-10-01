package com.projetos.marvel;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.projetos.marvel.ai.FirebaseInterviewSender;
import com.projetos.marvel.ui.room.InterviewSender;
import com.projetos.marvel.ui.room.RoomFlowController;

public class MainActivity extends AppCompatActivity {
    private RoomFlowController roomFlowController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);

        Object retained = getLastCustomNonConfigurationInstance();
        if (retained instanceof RoomFlowController) {
            roomFlowController = (RoomFlowController) retained;
            roomFlowController.setInterviewSender(new FirebaseInterviewSender());
            roomFlowController.attach(this, root);
        } else {
            roomFlowController = new RoomFlowController(this, root);
            roomFlowController.setInterviewSender(new FirebaseInterviewSender());
            roomFlowController.bind();
        }

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

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (isChangingConfigurations() && roomFlowController != null) {
            roomFlowController.retainForConfigurationChange();
            return roomFlowController;
        }
        return super.onRetainCustomNonConfigurationInstance();
    }

    /** Injection point for the real interview service when credentials are configured. */
    public void setInterviewSender(InterviewSender sender) {
        if (roomFlowController != null) roomFlowController.setInterviewSender(sender);
    }
}
