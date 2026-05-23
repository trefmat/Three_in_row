package io.trefmat.three_in_row;

import com.badlogic.gdx.files.FileHandle;

public interface MusicImportHandler {
    void requestMusicImport(MusicImportCallback callback);

    interface MusicImportCallback {
        void onMusicImported(FileHandle file);

        void onMusicImportFailed(String message);
    }
}
