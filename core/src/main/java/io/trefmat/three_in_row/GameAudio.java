package io.trefmat.three_in_row;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

final class GameAudio {
    private static final String PREFERENCES_NAME = "three_in_row_audio";
    private static final String TRACK_ENABLED_PREFIX = "track_enabled_";

    private final List<FileHandle> trackFiles = new ArrayList<FileHandle>();
    private final List<Boolean> trackEnabled = new ArrayList<Boolean>();
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
            Collections.sort(trackFiles, new Comparator<FileHandle>() {
                @Override
                public int compare(FileHandle first, FileHandle second) {
                    return first.name().compareToIgnoreCase(second.name());
                }
            });
            for (FileHandle track : trackFiles) {
                trackEnabled.add(loadTrackEnabled(track));
            }
            FileHandle sfx = musicFolder.child("soft-pop-sfx.mp3");
            if (sfx.exists()) {
                destroySound = Gdx.audio.newSound(sfx);
            }
        }
        if (!trackFiles.isEmpty() && enabledTrackCount() == 0) {
            trackEnabled.set(0, true);
            saveTrackEnabled(trackFiles.get(0), true);
        }
        if (hasEnabledTrack()) {
            playTrack(nextEnabledIndex(-1, 1));
        }
    }

    boolean addCustomTrack(FileHandle file) {
        if (file == null || !file.exists() || !isAudioFile(file.name().toLowerCase())) {
            return false;
        }
        for (FileHandle existing : trackFiles) {
            if (existing.path().equals(file.path())) {
                return false;
            }
        }
        trackFiles.add(file);
        trackEnabled.add(true);
        saveTrackEnabled(file, true);
        if (currentTrack == null) {
            playTrack(trackFiles.size() - 1);
        }
        return true;
    }

    void update() {
        if (currentTrack != null && !currentTrack.isPlaying()) {
            nextTrack();
        }
    }

    void nextTrack() {
        if (!hasEnabledTrack()) {
            return;
        }
        playTrack(nextEnabledIndex(trackIndex, 1));
    }

    void previousTrack() {
        if (!hasEnabledTrack()) {
            return;
        }
        playTrack(nextEnabledIndex(trackIndex, -1));
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
        if (!hasEnabledTrack()) {
            return "NO MUSIC";
        }
        return enabledPosition(trackIndex) + "/" + enabledTrackCount();
    }

    String currentTrackName() {
        if (!hasEnabledTrack()) {
            return "NO MUSIC";
        }
        return trackFiles.get(trackIndex).nameWithoutExtension();
    }

    int trackCount() {
        return trackFiles.size();
    }

    int enabledTrackCount() {
        int count = 0;
        for (Boolean enabled : trackEnabled) {
            if (enabled.booleanValue()) {
                count++;
            }
        }
        return count;
    }

    String trackName(int index) {
        if (index < 0 || index >= trackFiles.size()) {
            return "";
        }
        return trackFiles.get(index).nameWithoutExtension();
    }

    boolean isTrackEnabled(int index) {
        return index >= 0 && index < trackEnabled.size() && trackEnabled.get(index).booleanValue();
    }

    boolean toggleTrackEnabled(int index) {
        if (index < 0 || index >= trackFiles.size()) {
            return false;
        }
        boolean nextState = !trackEnabled.get(index).booleanValue();
        if (!nextState && enabledTrackCount() <= 1) {
            return false;
        }
        trackEnabled.set(index, nextState);
        saveTrackEnabled(trackFiles.get(index), nextState);
        if (!nextState && index == trackIndex) {
            nextTrack();
        } else if (nextState && currentTrack == null) {
            playTrack(index);
        }
        return true;
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
        if (index < 0 || index >= trackFiles.size() || !isTrackEnabled(index)) {
            return;
        }
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

    private boolean hasEnabledTrack() {
        return enabledTrackCount() > 0;
    }

    private int nextEnabledIndex(int startIndex, int direction) {
        if (trackFiles.isEmpty()) {
            return -1;
        }
        int index = startIndex;
        for (int i = 0; i < trackFiles.size(); i++) {
            index = (index + direction + trackFiles.size()) % trackFiles.size();
            if (isTrackEnabled(index)) {
                return index;
            }
        }
        return -1;
    }

    private int enabledPosition(int index) {
        int position = 0;
        for (int i = 0; i < trackFiles.size(); i++) {
            if (isTrackEnabled(i)) {
                position++;
            }
            if (i == index) {
                return position;
            }
        }
        return 0;
    }

    private boolean loadTrackEnabled(FileHandle track) {
        return preferences().getBoolean(trackEnabledKey(track), true);
    }

    private void saveTrackEnabled(FileHandle track, boolean enabled) {
        Preferences preferences = preferences();
        preferences.putBoolean(trackEnabledKey(track), enabled);
        preferences.flush();
    }

    private Preferences preferences() {
        return Gdx.app.getPreferences(PREFERENCES_NAME);
    }

    private String trackEnabledKey(FileHandle track) {
        return TRACK_ENABLED_PREFIX + track.path().replace('\\', '/');
    }

    private float clampVolume(float volume) {
        return Math.max(0f, Math.min(1f, volume));
    }
}
