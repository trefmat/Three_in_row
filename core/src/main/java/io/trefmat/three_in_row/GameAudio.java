package io.trefmat.three_in_row;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

final class GameAudio {
    private final List<FileHandle> trackFiles = new ArrayList<FileHandle>();
    private Music currentTrack;
    private Sound destroySound;
    private int trackIndex;
    private float musicVolume = 0.55f;
    private float destroyVolume = 0.75f;

    void load() {
        FileHandle musicFolder = Gdx.files.internal("music");
        if (musicFolder.exists() && musicFolder.isDirectory()) {
            for (FileHandle file : musicFolder.list()) {
                String name = file.name().toLowerCase();
                if (isAudioFile(name) && !name.contains("sfx")) {
                    trackFiles.add(file);
                }
            }
            FileHandle sfx = musicFolder.child("soft-pop-sfx.mp3");
            if (sfx.exists()) {
                destroySound = Gdx.audio.newSound(sfx);
            }
        }
        if (!trackFiles.isEmpty()) {
            playTrack(0);
        }
    }

    void update() {
        if (currentTrack != null && !currentTrack.isPlaying()) {
            nextTrack();
        }
    }

    void nextTrack() {
        if (trackFiles.isEmpty()) {
            return;
        }
        playTrack((trackIndex + 1) % trackFiles.size());
    }

    void previousTrack() {
        if (trackFiles.isEmpty()) {
            return;
        }
        playTrack((trackIndex + trackFiles.size() - 1) % trackFiles.size());
    }

    void increaseMusicVolume() {
        setMusicVolume(musicVolume + 0.10f);
    }

    void decreaseMusicVolume() {
        setMusicVolume(musicVolume - 0.10f);
    }

    void increaseDestroyVolume() {
        destroyVolume = clampVolume(destroyVolume + 0.10f);
    }

    void decreaseDestroyVolume() {
        destroyVolume = clampVolume(destroyVolume - 0.10f);
    }

    void playDestroySound() {
        if (destroySound != null && destroyVolume > 0f) {
            destroySound.play(destroyVolume);
        }
    }

    float musicVolume() {
        return musicVolume;
    }

    float destroyVolume() {
        return destroyVolume;
    }

    String trackLabel() {
        if (trackFiles.isEmpty()) {
            return "NO MUSIC";
        }
        return (trackIndex + 1) + "/" + trackFiles.size();
    }

    void dispose() {
        if (currentTrack != null) {
            currentTrack.dispose();
        }
        if (destroySound != null) {
            destroySound.dispose();
        }
    }

    private void playTrack(int index) {
        if (currentTrack != null) {
            currentTrack.stop();
            currentTrack.dispose();
        }
        trackIndex = index;
        currentTrack = Gdx.audio.newMusic(trackFiles.get(trackIndex));
        currentTrack.setVolume(musicVolume);
        currentTrack.setOnCompletionListener(new Music.OnCompletionListener() {
            @Override
            public void onCompletion(Music music) {
                nextTrack();
            }
        });
        currentTrack.play();
    }

    private void setMusicVolume(float volume) {
        musicVolume = clampVolume(volume);
        if (currentTrack != null) {
            currentTrack.setVolume(musicVolume);
        }
    }

    private boolean isAudioFile(String name) {
        return name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav");
    }

    private float clampVolume(float volume) {
        return Math.max(0f, Math.min(1f, volume));
    }
}
