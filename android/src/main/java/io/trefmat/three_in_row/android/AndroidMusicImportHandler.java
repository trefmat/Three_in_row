package io.trefmat.three_in_row.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import com.badlogic.gdx.Gdx;
import io.trefmat.three_in_row.MusicImportHandler;
import io.trefmat.three_in_row.MusicImportHandler.MusicImportCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class AndroidMusicImportHandler implements MusicImportHandler {
    static final int REQUEST_MUSIC = 7712;

    private final Activity activity;
    private MusicImportCallback callback;

    AndroidMusicImportHandler(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void requestMusicImport(MusicImportCallback callback) {
        this.callback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        activity.startActivityForResult(intent, REQUEST_MUSIC);
    }

    void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_MUSIC || callback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            postFailed("Music loading cancelled");
            return;
        }

        Uri uri = data.getData();
        try {
            activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }

        try {
            File folder = new File(activity.getFilesDir(), "custom_music");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            String name = sanitizeFileName(displayName(uri));
            File target = new File(folder, name);
            InputStream input = activity.getContentResolver().openInputStream(uri);
            FileOutputStream output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            output.close();
            postImported(target);
        } catch (Exception exception) {
            postFailed("Could not load music");
        }
    }

    private String displayName(Uri uri) {
        Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index);
                }
            } finally {
                cursor.close();
            }
        }
        return "custom_music.mp3";
    }

    private String sanitizeFileName(String name) {
        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safeName.endsWith(".mp3") && !safeName.endsWith(".ogg") && !safeName.endsWith(".wav")) {
            safeName += ".mp3";
        }
        return safeName;
    }

    private void postImported(final File file) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                callback.onMusicImported(Gdx.files.absolute(file.getAbsolutePath()));
            }
        });
    }

    private void postFailed(final String message) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                callback.onMusicImportFailed(message);
            }
        });
    }
}
