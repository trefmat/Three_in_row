package io.trefmat.three_in_row.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import com.badlogic.gdx.Gdx;
import io.trefmat.three_in_row.MusicImportHandler;
import io.trefmat.three_in_row.MusicImportHandler.ImageImportCallback;
import io.trefmat.three_in_row.MusicImportHandler.MusicImportCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class AndroidMusicImportHandler implements MusicImportHandler {
    static final int REQUEST_MUSIC = 7712;
    static final int REQUEST_IMAGE = 7713;

    private final Activity activity;
    private MusicImportCallback musicCallback;
    private ImageImportCallback imageCallback;

    AndroidMusicImportHandler(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void requestMusicImport(MusicImportCallback callback) {
        this.musicCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        activity.startActivityForResult(intent, REQUEST_MUSIC);
    }

    @Override
    public void requestImageImport(ImageImportCallback callback) {
        this.imageCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        activity.startActivityForResult(intent, REQUEST_IMAGE);
    }

    void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_MUSIC && requestCode != REQUEST_IMAGE) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            postFailed(requestCode, requestCode == REQUEST_IMAGE ? "Image loading cancelled" : "Music loading cancelled");
            return;
        }

        Uri uri = data.getData();
        try {
            activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }

        try {
            boolean image = requestCode == REQUEST_IMAGE;
            File folder = new File(activity.getFilesDir(), image ? "custom_gems" : "custom_music");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            String name = sanitizeFileName(displayName(uri), image);
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
            postImported(requestCode, target);
        } catch (Exception exception) {
            postFailed(requestCode, requestCode == REQUEST_IMAGE ? "Could not load image" : "Could not load music");
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
        return "custom_file";
    }

    private String sanitizeFileName(String name, boolean image) {
        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        String lower = safeName.toLowerCase();
        if (image) {
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".webp")) {
                safeName += ".png";
            }
        } else if (!lower.endsWith(".mp3") && !lower.endsWith(".ogg") && !lower.endsWith(".wav")) {
            safeName += ".mp3";
        }
        return safeName;
    }

    private void postImported(final int requestCode, final File file) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (requestCode == REQUEST_IMAGE && imageCallback != null) {
                    imageCallback.onImageImported(Gdx.files.absolute(file.getAbsolutePath()));
                    imageCallback = null;
                } else if (musicCallback != null) {
                    musicCallback.onMusicImported(Gdx.files.absolute(file.getAbsolutePath()));
                    musicCallback = null;
                }
            }
        });
    }

    private void postFailed(final int requestCode, final String message) {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (requestCode == REQUEST_IMAGE && imageCallback != null) {
                    imageCallback.onImageImportFailed(message);
                    imageCallback = null;
                } else if (musicCallback != null) {
                    musicCallback.onMusicImportFailed(message);
                    musicCallback = null;
                }
            }
        });
    }
}
