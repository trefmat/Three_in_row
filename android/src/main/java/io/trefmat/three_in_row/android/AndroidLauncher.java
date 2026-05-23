package io.trefmat.three_in_row.android;

import android.os.Bundle;
import android.content.Intent;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import io.trefmat.three_in_row.Main;

/** Launches the Android application. */
public class AndroidLauncher extends AndroidApplication {
    private AndroidMusicImportHandler musicImportHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true; // Recommended, but not required.
        musicImportHandler = new AndroidMusicImportHandler(this);
        initialize(new Main(musicImportHandler), configuration);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (musicImportHandler != null) {
            musicImportHandler.handleActivityResult(requestCode, resultCode, data);
        }
    }
}
