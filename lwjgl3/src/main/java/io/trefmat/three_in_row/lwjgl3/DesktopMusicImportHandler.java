package io.trefmat.three_in_row.lwjgl3;

import com.badlogic.gdx.Gdx;
import io.trefmat.three_in_row.MusicImportHandler;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

final class DesktopMusicImportHandler implements MusicImportHandler {
    @Override
    public void requestMusicImport(final MusicImportHandler.MusicImportCallback callback) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("Audio files", "mp3", "ogg", "wav"));
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    final File file = chooser.getSelectedFile();
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            callback.onMusicImported(Gdx.files.absolute(file.getAbsolutePath()));
                        }
                    });
                } else {
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            callback.onMusicImportFailed("Music loading cancelled");
                        }
                    });
                }
            }
        });
    }
}
