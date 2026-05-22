package io.trefmat.three_in_row;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

final class GameLayout {
    private GameLayout() {
    }

    static float topHudHeight() {
        float scale = uiScale();
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.18f, 104f * scale, 200f * scale);
    }

    static float bottomHudHeight() {
        float scale = uiScale();
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.13f, 82f * scale, 150f * scale);
    }

    static float cellSize() {
        float availableWidth = Gdx.graphics.getWidth() * 0.92f;
        float availableHeight = Gdx.graphics.getHeight() - topHudHeight() - bottomHudHeight();
        return Math.max(28f * uiScale(), Math.min(availableWidth, availableHeight) / GameConfig.BOARD_SIZE);
    }

    static float boardX() {
        return (Gdx.graphics.getWidth() - cellSize() * GameConfig.BOARD_SIZE) * 0.5f;
    }

    static float boardY() {
        float playBottom = bottomHudHeight();
        float playHeight = Gdx.graphics.getHeight() - topHudHeight() - bottomHudHeight();
        return playBottom + (playHeight - cellSize() * GameConfig.BOARD_SIZE) * 0.48f;
    }

    static float restartButtonWidth() {
        return restartButtonSize();
    }

    static float restartButtonHeight() {
        return restartButtonSize();
    }

    static float restartButtonSize() {
        float scale = uiScale();
        return MathUtils.clamp(shortSide() * 0.18f, 64f * scale, 132f * scale);
    }

    static float restartButtonX() {
        return (Gdx.graphics.getWidth() - restartButtonWidth()) * 0.5f;
    }

    static float restartButtonY() {
        return Math.max(14f * uiScale(), bottomHudHeight() * 0.18f);
    }

    static boolean isRestartButtonHit(int screenX, int worldY) {
        return screenX >= restartButtonX()
            && screenX <= restartButtonX() + restartButtonWidth()
            && worldY >= restartButtonY()
            && worldY <= restartButtonY() + restartButtonHeight();
    }

    static boolean isRestartButtonHovering() {
        return isRestartButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    static float hudMenuButtonWidth() {
        float scale = uiScale();
        return MathUtils.clamp(shortSide() * 0.34f, 110f * scale, 190f * scale);
    }

    static float hudMenuButtonHeight() {
        return restartButtonHeight();
    }

    static float hudMenuButtonX() {
        return Math.max(12f * uiScale(), restartButtonX() - hudMenuButtonWidth() - menuButtonGap());
    }

    static float hudMenuButtonY() {
        return restartButtonY();
    }

    static boolean isMenuButtonHit(int screenX, int worldY) {
        return screenX >= hudMenuButtonX()
            && screenX <= hudMenuButtonX() + hudMenuButtonWidth()
            && worldY >= hudMenuButtonY()
            && worldY <= hudMenuButtonY() + hudMenuButtonHeight();
    }

    static boolean isHudMenuButtonHovering() {
        return isMenuButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    static float menuButtonWidth() {
        float scale = uiScale();
        return MathUtils.clamp(menuPanelWidth() * 0.76f, 230f * scale, 390f * scale);
    }

    static float menuActionButtonHeight() {
        float scale = uiScale();
        return MathUtils.clamp(shortSide() * 0.105f, 54f * scale, 82f * scale);
    }

    static float menuPanelWidth() {
        float scale = uiScale();
        float sidePadding = 28f * scale;
        return Math.min(Gdx.graphics.getWidth() - sidePadding * 2f, MathUtils.clamp(Gdx.graphics.getWidth() * 0.82f, 300f * scale, 500f * scale));
    }

    static float menuPanelHeight() {
        float scale = uiScale();
        float sidePadding = 28f * scale;
        return Math.min(Gdx.graphics.getHeight() - sidePadding * 2f, MathUtils.clamp(Gdx.graphics.getHeight() * 0.58f, 430f * scale, 620f * scale));
    }

    static float menuPanelX() {
        return (Gdx.graphics.getWidth() - menuPanelWidth()) * 0.5f;
    }

    static float menuPanelY() {
        return (Gdx.graphics.getHeight() - menuPanelHeight()) * 0.5f;
    }

    static float playButtonX() {
        return (Gdx.graphics.getWidth() - menuButtonWidth()) * 0.5f;
    }

    static float playButtonY() {
        float groupHeight = menuActionButtonHeight() * 3f + menuButtonGap() * 2f;
        return menuPanelY() + menuPanelHeight() * 0.12f + groupHeight - menuActionButtonHeight();
    }

    static float settingsButtonX() {
        return playButtonX();
    }

    static float settingsButtonY() {
        return playButtonY() - menuActionButtonHeight() - menuButtonGap();
    }

    static float exitButtonX() {
        return playButtonX();
    }

    static float exitButtonY() {
        return settingsButtonY() - menuActionButtonHeight() - menuButtonGap();
    }

    static float menuButtonGap() {
        float scale = uiScale();
        return MathUtils.clamp(menuPanelHeight() * 0.06f, 16f * scale, 24f * scale);
    }

    static boolean isPlayButtonHit(int screenX, int worldY) {
        return screenX >= playButtonX()
            && screenX <= playButtonX() + menuButtonWidth()
            && worldY >= playButtonY()
            && worldY <= playButtonY() + menuActionButtonHeight();
    }

    static boolean isPlayButtonHovering() {
        return isPlayButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    static boolean isSettingsButtonHit(int screenX, int worldY) {
        return screenX >= settingsButtonX()
            && screenX <= settingsButtonX() + menuButtonWidth()
            && worldY >= settingsButtonY()
            && worldY <= settingsButtonY() + menuActionButtonHeight();
    }

    static boolean isSettingsButtonHovering() {
        return isSettingsButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    static boolean isExitButtonHit(int screenX, int worldY) {
        return screenX >= exitButtonX()
            && screenX <= exitButtonX() + menuButtonWidth()
            && worldY >= exitButtonY()
            && worldY <= exitButtonY() + menuActionButtonHeight();
    }

    static boolean isExitButtonHovering() {
        return isExitButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    static float hudScale(float multiplier) {
        return multiplier * MathUtils.clamp(Math.min(Gdx.graphics.getWidth() / 540f, Gdx.graphics.getHeight() / 960f), 0.72f, 1.32f);
    }

    static float uiScale() {
        return MathUtils.clamp(Math.min(Gdx.graphics.getWidth() / 720f, Gdx.graphics.getHeight() / 1280f), 0.55f, 1.15f);
    }

    static float shortSide() {
        return Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
