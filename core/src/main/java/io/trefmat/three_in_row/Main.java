package io.trefmat.three_in_row;

import static io.trefmat.three_in_row.GameLayout.*;
import static io.trefmat.three_in_row.GameAssets.*;
import static io.trefmat.three_in_row.CellType.*;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import io.trefmat.three_in_row.MusicImportHandler.MusicImportCallback;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final String GAME_PREFERENCES_NAME = "three_in_row_game";
    private static final String SAVED_LEVEL_KEY = "saved_level_index";
    static final int BOARD_SIZE = 8;
    static final int GEM_TYPES = 6;

    private enum GameScreen {
        MENU,
        SETTINGS,
        PLAYING
    }

    private enum AnimationState {
        IDLE,
        VALID_SWAP,
        LIGHTNING_SWAP,
        BOOSTER_SWAP,
        INVALID_SWAP_OUT,
        INVALID_SWAP_BACK,
        MATCH_CLEAR,
        BOOSTER_BLAST,
        FALLING
    }

    private static final String[] GEM_COLOR_NAMES = {"blue", "green", "red", "yellow", "purple", "orange"};
    private static final float INVALID_FLASH_TIME = 0.25f;
    private static final float SWAP_TIME = 0.18f;
    private static final float FALL_TIME = 0.28f;
    private static final float INVALID_SWAP_TIME = 0.13f;
    private static final float MATCH_CLEAR_TIME = 0.22f;
    private static final float BOOSTER_BLAST_TIME = 0.62f;
    private static final float RESTART_ANIMATION_TIME = 1.05f;
    private static final float BOOSTER_DRAW_SCALE = 1.16f;
    private static final float ROCKET_DRAW_SCALE = 1.30f;
    private static final int[] LEVEL_TARGET_COUNTS = {12, 14, 16, 18, 20, 22, 26, 30, 34, 40};
    private static final int AUDIO_PREV = 0;
    private static final int AUDIO_NEXT = 1;
    private static final int SETTINGS_BGM_DOWN = 0;
    private static final int SETTINGS_BGM_UP = 1;
    private static final int SETTINGS_SFX_DOWN = 2;
    private static final int SETTINGS_SFX_UP = 3;
    private static final int SETTINGS_LOAD_MUSIC = 4;
    private static final int SETTINGS_BACK = 5;
    private static final int SETTINGS_TRACK_PREV = 6;
    private static final int SETTINGS_TRACK_NEXT = 7;
    private static final int SETTINGS_TRACK_ROWS = 3;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private final float[][] drawRows = new float[BOARD_SIZE][BOARD_SIZE];
    private final float[][] drawCols = new float[BOARD_SIZE][BOARD_SIZE];
    private final float[][] startRows = new float[BOARD_SIZE][BOARD_SIZE];
    private final float[][] startCols = new float[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] pendingCellsToClear = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] pendingMatches = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final int[][] pendingBoosters = new int[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] blastCells = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] blastHorizontalRockets = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] blastVerticalRockets = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final boolean[][] blastBombs = new boolean[BOARD_SIZE][BOARD_SIZE];
    private final Texture[] gemTextures = new Texture[GEM_TYPES];
    private final Texture[] rocketHorizontalTextures = new Texture[GEM_TYPES];
    private final Texture[] rocketVerticalTextures = new Texture[GEM_TYPES];
    private final Texture[] bombTextures = new Texture[GEM_TYPES];
    private final Texture[] lightningTextures = new Texture[GEM_TYPES];

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyphLayout;
    private GameAudio audio;
    private MusicImportHandler musicImportHandler;
    private Texture background;
    private Texture restartIcon;
    private Texture plusIcon;
    private Texture minusIcon;
    private Texture nextIcon;
    private Texture previousIcon;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private int levelIndex;
    private int levelCollected;
    private int score;
    private int moves;
    private int settingsTrackScroll;
    private float invalidFlash;
    private float menuTime;
    private String statusText = "Pick a crystal";
    private GameScreen gameScreen = GameScreen.MENU;
    private AnimationState animationState = AnimationState.IDLE;
    private float animationTimer;
    private float animationDuration;
    private int swapRowA;
    private int swapColA;
    private int swapRowB;
    private int swapColB;
    private int lightningTargetType = -1;
    private boolean lightningClearsBoard;
    private int boosterPreferredRow = -1;
    private int boosterPreferredCol = -1;
    private int boosterFallbackRow = -1;
    private int boosterFallbackCol = -1;
    private int touchStartX;
    private int touchStartY;
    private int touchStartRow = -1;
    private int touchStartCol = -1;
    private int activeTouchPointer = -1;
    private boolean swipeHandled;
    private boolean restartAnimating;
    private boolean restartBoardRebuilt;
    private float restartAnimationTimer;

    public Main(MusicImportHandler musicImportHandler) {
        this.musicImportHandler = musicImportHandler;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1f);
        glyphLayout = new GlyphLayout();
        audio = new GameAudio();
        audio.load();
        background = createBackgroundTexture();
        restartIcon = new Texture(Gdx.files.internal("restart.png"));
        restartIcon.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        plusIcon = loadUiTexture("media/plus.png");
        minusIcon = loadUiTexture("media/minus.png");
        nextIcon = loadUiTexture("media/next.png");
        previousIcon = loadUiTexture("media/previous.png");

        Color[] colors = {
            Color.valueOf("29D6FF"),
            Color.valueOf("4CF07A"),
            Color.valueOf("FF4F6D"),
            Color.valueOf("FFD84A"),
            Color.valueOf("A967FF"),
            Color.valueOf("FF8B3D")
        };
        String[] gemFiles = {
            "gems/blue.png",
            "gems/green.png",
            "gems/red.png",
            "gems/yellow.png",
            "gems/purple.png",
            "gems/orange.png"
        };
        for (int i = 0; i < GEM_TYPES; i++) {
            gemTextures[i] = loadGemTexture(gemFiles[i]);
            rocketVerticalTextures[i] = loadRocketTexture(GEM_COLOR_NAMES[i], colors[i], false);
            rocketHorizontalTextures[i] = rocketVerticalTextures[i];
            bombTextures[i] = loadBombTexture(GEM_COLOR_NAMES[i], colors[i]);
            lightningTextures[i] = loadLightningTexture(GEM_COLOR_NAMES[i], colors[i]);
        }

        levelIndex = loadSavedLevelIndex();
        resetBoard();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT || button == -1) {
                    beginTouch(screenX, screenY, pointer);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (pointer == activeTouchPointer) {
                    handleSwipeDrag(screenX, screenY);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (pointer == activeTouchPointer) {
                    finishTouch(screenX, screenY);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (gameScreen == GameScreen.MENU) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                        startGame();
                        return true;
                    }
                    if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                        Gdx.app.exit();
                        return true;
                    }
                    return false;
                }

                if (gameScreen == GameScreen.SETTINGS) {
                    if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                        openMainMenu();
                        return true;
                    }
                    return false;
                }

                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    openMainMenu();
                    return true;
                }
                if (keycode == Input.Keys.R) {
                    beginRestartAnimation();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        menuTime += delta;
        audio.update();

        if (gameScreen == GameScreen.MENU) {
            Gdx.gl.glClearColor(0.045f, 0.05f, 0.075f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            drawBackground();
            drawMainMenu();
            return;
        }

        if (gameScreen == GameScreen.SETTINGS) {
            Gdx.gl.glClearColor(0.045f, 0.05f, 0.075f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            drawBackground();
            drawSettingsMenu();
            return;
        }

        invalidFlash = Math.max(0f, invalidFlash - delta);
        updateRestartAnimation(delta);
        updateAnimation(delta);

        Gdx.gl.glClearColor(0.045f, 0.05f, 0.075f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawBackground();
        drawBoardBackground();
        drawGems();
        drawBoosterEffects();
        drawHud();
    }

    private void handleBoardTap(int screenX, int screenY) {
        int worldY = Gdx.graphics.getHeight() - screenY;
        if (handleAudioButtonTap(screenX, worldY)) {
            return;
        }

        if (restartAnimating) {
            return;
        }

        if (animationState != AnimationState.IDLE) {
            return;
        }

        if (isRestartButtonHit(screenX, worldY)) {
            beginRestartAnimation();
            return;
        }
        if (isMenuButtonHit(screenX, worldY)) {
            openMainMenu();
            return;
        }

        int col = screenToCol(screenX);
        int row = screenToRow(screenY);
        if (!isInside(row, col)) {
            selectedRow = -1;
            selectedCol = -1;
            statusText = "Tap a board cell";
            return;
        }

        if (selectedRow == -1) {
            selectedRow = row;
            selectedCol = col;
            statusText = "Pick a neighboring crystal";
            return;
        }

        if (selectedRow == row && selectedCol == col) {
            selectedRow = -1;
            selectedCol = -1;
            statusText = "Selection cleared";
            return;
        }

        if (areAdjacent(selectedRow, selectedCol, row, col)) {
            beginSwap(selectedRow, selectedCol, row, col);
            selectedRow = -1;
            selectedCol = -1;
        } else {
            selectedRow = row;
            selectedCol = col;
            statusText = "New crystal selected";
        }
    }

    private boolean handleAudioButtonTap(int screenX, int worldY) {
        for (int button = AUDIO_PREV; button <= AUDIO_NEXT; button++) {
            if (isAudioButtonHit(button, screenX, worldY)) {
                if (button == AUDIO_PREV) {
                    audio.previousTrack();
                } else {
                    audio.nextTrack();
                }
                return true;
            }
        }
        return false;
    }

    private void beginTouch(int screenX, int screenY, int pointer) {
        activeTouchPointer = pointer;
        touchStartX = screenX;
        touchStartY = screenY;
        touchStartCol = screenToCol(screenX);
        touchStartRow = screenToRow(screenY);
        swipeHandled = false;
    }

    private void finishTouch(int screenX, int screenY) {
        if (gameScreen == GameScreen.MENU) {
            handleMenuTap(screenX, Gdx.graphics.getHeight() - screenY);
            activeTouchPointer = -1;
            swipeHandled = false;
            return;
        }

        if (gameScreen == GameScreen.SETTINGS) {
            handleSettingsTap(screenX, Gdx.graphics.getHeight() - screenY);
            activeTouchPointer = -1;
            swipeHandled = false;
            return;
        }

        if (!swipeHandled) {
            handleBoardTap(screenX, screenY);
        }
        activeTouchPointer = -1;
        touchStartRow = -1;
        touchStartCol = -1;
        swipeHandled = false;
    }

    private void handleSwipeDrag(int screenX, int screenY) {
        if (gameScreen == GameScreen.MENU || gameScreen == GameScreen.SETTINGS) {
            return;
        }

        if (swipeHandled || animationState != AnimationState.IDLE || !isInside(touchStartRow, touchStartCol)) {
            return;
        }

        float deltaX = screenX - touchStartX;
        float deltaY = screenY - touchStartY;
        float threshold = cellSize() * 0.34f;
        if (Math.max(Math.abs(deltaX), Math.abs(deltaY)) < threshold) {
            return;
        }

        int targetRow = touchStartRow;
        int targetCol = touchStartCol;
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            targetCol += deltaX > 0f ? 1 : -1;
        } else {
            targetRow += deltaY < 0f ? 1 : -1;
        }

        if (!isInside(targetRow, targetCol)) {
            swipeHandled = true;
            selectedRow = -1;
            selectedCol = -1;
            statusText = "Swipe inside the board";
            return;
        }

        selectedRow = -1;
        selectedCol = -1;
        beginSwap(touchStartRow, touchStartCol, targetRow, targetCol);
        swipeHandled = true;
    }

    private void beginSwap(int rowA, int colA, int rowB, int colB) {
        swapRowA = rowA;
        swapColA = colA;
        swapRowB = rowB;
        swapColB = colB;

        boolean lightningSwap = isLightning(board[rowA][colA]) || isLightning(board[rowB][colB]);
        boolean boosterSwap = isBooster(board[rowA][colA]) && isBooster(board[rowB][colB]);
        if (lightningSwap) {
            lightningClearsBoard = isLightning(board[rowA][colA]) && isLightning(board[rowB][colB]);
            if (lightningClearsBoard) {
                lightningTargetType = -1;
            } else if (isLightning(board[rowA][colA])) {
                lightningTargetType = baseGemType(board[rowB][colB]);
            } else {
                lightningTargetType = baseGemType(board[rowA][colA]);
            }
        }
        swap(rowA, colA, rowB, colB);

        if (lightningSwap) {
            moves++;
            drawRows[rowA][colA] = rowB;
            drawCols[rowA][colA] = colB;
            drawRows[rowB][colB] = rowA;
            drawCols[rowB][colB] = colA;
            captureAnimationStarts();
            beginAnimation(AnimationState.LIGHTNING_SWAP, SWAP_TIME);
            statusText = "Lightning charged";
            return;
        }

        if (boosterSwap) {
            moves++;
            drawRows[rowA][colA] = rowB;
            drawCols[rowA][colA] = colB;
            drawRows[rowB][colB] = rowA;
            drawCols[rowB][colB] = colA;
            captureAnimationStarts();
            beginAnimation(AnimationState.BOOSTER_SWAP, SWAP_TIME);
            statusText = "Boosters combined";
            return;
        }

        boolean[][] matches = findMatches();
        if (!hasMatches(matches)) {
            swap(rowA, colA, rowB, colB);
            captureAnimationStarts();
            beginAnimation(AnimationState.INVALID_SWAP_OUT, INVALID_SWAP_TIME);
            statusText = "Swap must create 3 in a row";
            return;
        }

        moves++;
        boosterPreferredRow = rowB;
        boosterPreferredCol = colB;
        boosterFallbackRow = rowA;
        boosterFallbackCol = colA;
        drawRows[rowA][colA] = rowB;
        drawCols[rowA][colA] = colB;
        drawRows[rowB][colB] = rowA;
        drawCols[rowB][colB] = colA;
        captureAnimationStarts();
        beginAnimation(AnimationState.VALID_SWAP, SWAP_TIME);
        statusText = "Swapping crystals";
    }

    private void updateAnimation(float delta) {
        if (animationState == AnimationState.IDLE) {
            return;
        }

        animationTimer += delta;
        float progress = Math.min(1f, animationTimer / animationDuration);
        float eased = Interpolation.smooth.apply(progress);

        if (animationState == AnimationState.INVALID_SWAP_OUT) {
            animateInvalidSwapOut(eased);
        } else if (animationState == AnimationState.INVALID_SWAP_BACK) {
            animateInvalidSwapBack(eased);
        } else {
            animateToCells(eased);
        }

        if (progress < 1f) {
            return;
        }

        finishAnimationStep();
    }

    private void beginRestartAnimation() {
        restartAnimating = true;
        restartBoardRebuilt = false;
        restartAnimationTimer = 0f;
        selectedRow = -1;
        selectedCol = -1;
    }

    private void updateRestartAnimation(float delta) {
        if (!restartAnimating) {
            return;
        }

        restartAnimationTimer += delta;
        if (!restartBoardRebuilt && restartAnimationProgress() >= 0.52f) {
            restartBoardRebuilt = true;
            resetBoard();
            restartAnimating = true;
        }
        if (restartAnimationTimer >= RESTART_ANIMATION_TIME) {
            restartAnimating = false;
            restartBoardRebuilt = false;
            restartAnimationTimer = 0f;
        }
    }

    private void finishAnimationStep() {
        if (animationState == AnimationState.INVALID_SWAP_OUT) {
            captureAnimationStarts();
            beginAnimation(AnimationState.INVALID_SWAP_BACK, INVALID_SWAP_TIME);
            return;
        }

        if (animationState == AnimationState.INVALID_SWAP_BACK) {
            syncDrawPositions();
            invalidFlash = INVALID_FLASH_TIME;
            animationState = AnimationState.IDLE;
            return;
        }

        if (animationState == AnimationState.VALID_SWAP) {
            syncDrawPositions();
            beginNextCascade();
            return;
        }

        if (animationState == AnimationState.LIGHTNING_SWAP) {
            syncDrawPositions();
            beginLightningBlast();
            return;
        }

        if (animationState == AnimationState.BOOSTER_SWAP) {
            syncDrawPositions();
            beginBoosterSwapBlast();
            return;
        }

        if (animationState == AnimationState.MATCH_CLEAR) {
            finishMatchClear();
            return;
        }

        if (animationState == AnimationState.BOOSTER_BLAST) {
            finishBoosterBlast();
            return;
        }

        if (animationState == AnimationState.FALLING) {
            syncDrawPositions();
            beginNextCascade();
        }
    }

    private void beginNextCascade() {
        boolean[][] matches = findMatches();
        if (hasMatches(matches)) {
            int removed = clearMatchesAndCreateBoosters();
            if (removed == -1) {
                return;
            }
            score += removed * 10;
            collapseAndFillAnimated();
            beginAnimation(AnimationState.FALLING, FALL_TIME);
            statusText = "Crystals falling";
            return;
        }

        if (isLevelComplete()) {
            advanceLevel();
        } else if (!hasPossibleMove()) {
            refillBoardKeepingProgress();
            syncDrawPositions();
            statusText = "Board reshuffled";
        } else {
            statusText = "Pick a crystal";
        }
        animationState = AnimationState.IDLE;
    }

    private void beginAnimation(AnimationState state, float duration) {
        animationState = state;
        animationTimer = 0f;
        animationDuration = duration;
    }

    private void finishBoosterBlast() {
        int removed = applyPendingClear();
        if (removed > 0) {
            audio.playDestroySound();
        }
        score += removed * 10;
        clearBlastEffects();
        collapseAndFillAnimated();
        beginAnimation(AnimationState.FALLING, FALL_TIME);
        statusText = "Crystals falling";
    }

    private void finishMatchClear() {
        int removed = applyPendingClear();
        if (removed > 0) {
            audio.playDestroySound();
        }
        score += removed * 10;
        clearBlastEffects();
        collapseAndFillAnimated();
        beginAnimation(AnimationState.FALLING, FALL_TIME);
        statusText = "Crystals falling";
    }

    private void animateToCells(float progress) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                drawRows[row][col] = MathUtils.lerp(startRows[row][col], row, progress);
                drawCols[row][col] = MathUtils.lerp(startCols[row][col], col, progress);
            }
        }
    }

    private void animateInvalidSwapOut(float progress) {
        drawRows[swapRowA][swapColA] = MathUtils.lerp(startRows[swapRowA][swapColA], swapRowB, progress);
        drawCols[swapRowA][swapColA] = MathUtils.lerp(startCols[swapRowA][swapColA], swapColB, progress);
        drawRows[swapRowB][swapColB] = MathUtils.lerp(startRows[swapRowB][swapColB], swapRowA, progress);
        drawCols[swapRowB][swapColB] = MathUtils.lerp(startCols[swapRowB][swapColB], swapColA, progress);
    }

    private void animateInvalidSwapBack(float progress) {
        drawRows[swapRowA][swapColA] = MathUtils.lerp(startRows[swapRowA][swapColA], swapRowA, progress);
        drawCols[swapRowA][swapColA] = MathUtils.lerp(startCols[swapRowA][swapColA], swapColA, progress);
        drawRows[swapRowB][swapColB] = MathUtils.lerp(startRows[swapRowB][swapColB], swapRowB, progress);
        drawCols[swapRowB][swapColB] = MathUtils.lerp(startCols[swapRowB][swapColB], swapColB, progress);
    }

    private void captureAnimationStarts() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                startRows[row][col] = drawRows[row][col];
                startCols[row][col] = drawCols[row][col];
            }
        }
    }

    private void syncDrawPositions() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                drawRows[row][col] = row;
                drawCols[row][col] = col;
                startRows[row][col] = row;
                startCols[row][col] = col;
            }
        }
    }

    private void collapseAndFillAnimated() {
        int[][] nextBoard = new int[BOARD_SIZE][BOARD_SIZE];
        float[][] nextDrawRows = new float[BOARD_SIZE][BOARD_SIZE];
        float[][] nextDrawCols = new float[BOARD_SIZE][BOARD_SIZE];

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                nextBoard[row][col] = EMPTY;
                nextDrawRows[row][col] = row;
                nextDrawCols[row][col] = col;
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            int writeRow = 0;
            for (int row = 0; row < BOARD_SIZE; row++) {
                if (board[row][col] != EMPTY) {
                    nextBoard[writeRow][col] = board[row][col];
                    nextDrawRows[writeRow][col] = drawRows[row][col];
                    nextDrawCols[writeRow][col] = drawCols[row][col];
                    writeRow++;
                }
            }

            int spawnOffset = 0;
            while (writeRow < BOARD_SIZE) {
                nextBoard[writeRow][col] = randomGem();
                nextDrawRows[writeRow][col] = BOARD_SIZE + spawnOffset;
                nextDrawCols[writeRow][col] = col;
                writeRow++;
                spawnOffset++;
            }
        }

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                board[row][col] = nextBoard[row][col];
                drawRows[row][col] = nextDrawRows[row][col];
                drawCols[row][col] = nextDrawCols[row][col];
            }
        }
        captureAnimationStarts();
    }

    private boolean[][] findMatches() {
        boolean[][] matches = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int row = 0; row < BOARD_SIZE; row++) {
            int runStart = 0;
            for (int col = 1; col <= BOARD_SIZE; col++) {
                boolean same = col < BOARD_SIZE && sameGemType(board[row][col], board[row][runStart]);
                if (!same) {
                    if (board[row][runStart] != EMPTY && col - runStart >= 3) {
                        for (int mark = runStart; mark < col; mark++) {
                            matches[row][mark] = true;
                        }
                    }
                    runStart = col;
                }
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            int runStart = 0;
            for (int row = 1; row <= BOARD_SIZE; row++) {
                boolean same = row < BOARD_SIZE && sameGemType(board[row][col], board[runStart][col]);
                if (!same) {
                    if (board[runStart][col] != EMPTY && row - runStart >= 3) {
                        for (int mark = runStart; mark < row; mark++) {
                            matches[mark][col] = true;
                        }
                    }
                    runStart = row;
                }
            }
        }

        return matches;
    }

    private boolean hasMatches(boolean[][] matches) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (matches[row][col]) {
                    return true;
                }
            }
        }
        return false;
    }

    private int clearMatchesAndCreateBoosters() {
        boolean[][] matches = findMatches();
        boolean[][] cellsToClear = copyMatches(matches);
        int[][] boosters = new int[BOARD_SIZE][BOARD_SIZE];
        clearBlastEffects();

        int bombPosition = chooseBombBoosterPosition(matches);
        if (bombPosition != -1) {
            int bombRow = bombPosition / BOARD_SIZE;
            int bombCol = bombPosition % BOARD_SIZE;
            boosters[bombRow][bombCol] = makeBomb(baseGemType(board[bombRow][bombCol]));
        }

        for (int row = 0; row < BOARD_SIZE; row++) {
            int runStart = 0;
            for (int col = 1; col <= BOARD_SIZE; col++) {
                boolean same = col < BOARD_SIZE && sameGemType(board[row][col], board[row][runStart]);
                if (!same) {
                    int runLength = col - runStart;
                    if (board[row][runStart] != EMPTY && runLength >= 5) {
                        int boosterCol = chooseBoosterCol(row, runStart, col);
                        boosters[row][boosterCol] = makeLightning(baseGemType(board[row][runStart]));
                    } else if (board[row][runStart] != EMPTY && runLength >= 4) {
                        int boosterCol = chooseBoosterCol(row, runStart, col);
                        if (boosters[row][boosterCol] == 0) {
                            boosters[row][boosterCol] = makeRocket(baseGemType(board[row][runStart]), true);
                        }
                    }
                    runStart = col;
                }
            }
        }

        for (int col = 0; col < BOARD_SIZE; col++) {
            int runStart = 0;
            for (int row = 1; row <= BOARD_SIZE; row++) {
                boolean same = row < BOARD_SIZE && sameGemType(board[row][col], board[runStart][col]);
                if (!same) {
                    int runLength = row - runStart;
                    if (board[runStart][col] != EMPTY && runLength >= 5) {
                        int boosterRow = chooseBoosterRow(col, runStart, row);
                        boosters[boosterRow][col] = makeLightning(baseGemType(board[runStart][col]));
                    } else if (board[runStart][col] != EMPTY && runLength >= 4) {
                        int boosterRow = chooseBoosterRow(col, runStart, row);
                        if (boosters[boosterRow][col] == 0) {
                            boosters[boosterRow][col] = makeRocket(baseGemType(board[runStart][col]), false);
                        }
                    }
                    runStart = row;
                }
            }
        }

        boolean activatedBooster = false;
        boolean[][] activatedBoosters = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (!matches[row][col]) {
                    continue;
                }
                if (isBooster(board[row][col])) {
                    markBoosterBlast(cellsToClear, activatedBoosters, row, col);
                    activatedBooster = true;
                }
            }
        }

        if (activatedBooster) {
            activateBoostersInBlast(cellsToClear, activatedBoosters);
            copyBooleanGrid(cellsToClear, pendingCellsToClear);
            copyBooleanGrid(matches, pendingMatches);
            copyIntGrid(boosters, pendingBoosters);
            copyBooleanGrid(cellsToClear, blastCells);
            clearBoosterPlacementHints();
            statusText = "Booster blast";
            beginAnimation(AnimationState.BOOSTER_BLAST, BOOSTER_BLAST_TIME);
            return -1;
        }

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        clearBoosterPlacementHints();
        statusText = "Match cleared";
        beginAnimation(AnimationState.MATCH_CLEAR, MATCH_CLEAR_TIME);
        return -1;
    }

    private void beginBoosterSwapBlast() {
        boolean[][] cellsToClear = new boolean[BOARD_SIZE][BOARD_SIZE];
        boolean[][] matches = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[][] boosters = new int[BOARD_SIZE][BOARD_SIZE];
        boolean[][] activatedBoosters = new boolean[BOARD_SIZE][BOARD_SIZE];
        clearBlastEffects();

        if (isBombRocketSwap()) {
            int rocketRow = isRocket(board[swapRowA][swapColA]) ? swapRowA : swapRowB;
            int rocketCol = isRocket(board[swapRowA][swapColA]) ? swapColA : swapColB;
            int bombRow = isBomb(board[swapRowA][swapColA]) ? swapRowA : swapRowB;
            int bombCol = isBomb(board[swapRowA][swapColA]) ? swapColA : swapColB;
            markCrossBlast(cellsToClear, rocketRow, rocketCol);
            blastBombs[bombRow][bombCol] = true;
            markBombBlast(cellsToClear, bombRow, bombCol);
        } else {
            markBoosterBlast(cellsToClear, activatedBoosters, swapRowA, swapColA);
            markBoosterBlast(cellsToClear, activatedBoosters, swapRowB, swapColB);
        }
        activateBoostersInBlast(cellsToClear, activatedBoosters);

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        clearBoosterPlacementHints();
        statusText = "Booster blast";
        beginAnimation(AnimationState.BOOSTER_BLAST, BOOSTER_BLAST_TIME);
    }

    private boolean isBombRocketSwap() {
        return (isBomb(board[swapRowA][swapColA]) && isRocket(board[swapRowB][swapColB]))
            || (isRocket(board[swapRowA][swapColA]) && isBomb(board[swapRowB][swapColB]));
    }

    private void beginLightningBlast() {
        boolean[][] cellsToClear = new boolean[BOARD_SIZE][BOARD_SIZE];
        boolean[][] matches = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[][] boosters = new int[BOARD_SIZE][BOARD_SIZE];
        boolean[][] activatedBoosters = new boolean[BOARD_SIZE][BOARD_SIZE];
        clearBlastEffects();

        if (lightningClearsBoard) {
            markWholeBoard(cellsToClear);
        } else {
            int otherRow = isLightning(board[swapRowA][swapColA]) ? swapRowB : swapRowA;
            int otherCol = isLightning(board[swapRowA][swapColA]) ? swapColB : swapColA;
            int otherCell = board[otherRow][otherCol];
            if (isRocket(otherCell)) {
                markLightningRocketCombo(cellsToClear, lightningTargetType, isHorizontalRocket(otherCell));
            } else if (isBomb(otherCell)) {
                markLightningBombCombo(cellsToClear, lightningTargetType);
            } else {
                markGemType(cellsToClear, lightningTargetType);
            }
        }
        cellsToClear[swapRowA][swapColA] = true;
        cellsToClear[swapRowB][swapColB] = true;
        activateBoostersInBlast(cellsToClear, activatedBoosters);

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        lightningTargetType = -1;
        lightningClearsBoard = false;
        clearBoosterPlacementHints();
        statusText = "Lightning strike";
        beginAnimation(AnimationState.BOOSTER_BLAST, BOOSTER_BLAST_TIME);
    }

    private void markLightningRocketCombo(boolean[][] cellsToClear, int gemType, boolean horizontal) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (baseGemType(board[row][col]) == gemType) {
                    board[row][col] = makeRocket(gemType, horizontal);
                    if (horizontal) {
                        blastHorizontalRockets[row][col] = true;
                    } else {
                        blastVerticalRockets[row][col] = true;
                    }
                    markRocketBlast(cellsToClear, row, col);
                }
            }
        }
    }

    private void markLightningBombCombo(boolean[][] cellsToClear, int gemType) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (baseGemType(board[row][col]) == gemType) {
                    board[row][col] = makeBomb(gemType);
                    blastBombs[row][col] = true;
                    markBombBlast(cellsToClear, row, col);
                }
            }
        }
    }

    private void activateBoostersInBlast(boolean[][] cellsToClear, boolean[][] activatedBoosters) {
        boolean activated;
        do {
            activated = false;
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (cellsToClear[row][col] && isBooster(board[row][col]) && !activatedBoosters[row][col]) {
                        markBoosterBlast(cellsToClear, activatedBoosters, row, col);
                        activated = true;
                    }
                }
            }
        } while (activated);
    }

    private void markBoosterBlast(boolean[][] cellsToClear, boolean[][] activatedBoosters, int row, int col) {
        if (!isInside(row, col) || activatedBoosters[row][col] || !isBooster(board[row][col])) {
            return;
        }
        activatedBoosters[row][col] = true;
        cellsToClear[row][col] = true;
        if (isRocket(board[row][col])) {
            if (isHorizontalRocket(board[row][col])) {
                blastHorizontalRockets[row][col] = true;
            } else {
                blastVerticalRockets[row][col] = true;
            }
            markRocketBlast(cellsToClear, row, col);
        } else if (isBomb(board[row][col])) {
            blastBombs[row][col] = true;
            markBombBlast(cellsToClear, row, col);
        } else if (isLightning(board[row][col])) {
            int gemType = baseGemType(board[row][col]);
            for (int clearRow = 0; clearRow < BOARD_SIZE; clearRow++) {
                for (int clearCol = 0; clearCol < BOARD_SIZE; clearCol++) {
                    if (baseGemType(board[clearRow][clearCol]) == gemType) {
                        cellsToClear[clearRow][clearCol] = true;
                    }
                }
            }
        }
    }

    private int applyPendingClear() {
        int removed = applyClear(pendingCellsToClear, pendingMatches, pendingBoosters);
        clearPendingClear();
        return removed;
    }

    private int applyClear(boolean[][] cellsToClear, boolean[][] matches, int[][] boosters) {
        int removed = 0;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (cellsToClear[row][col]) {
                    collectLevelTarget(board[row][col]);
                    removed++;
                    if (matches[row][col] && boosters[row][col] != 0 && !isBooster(board[row][col])) {
                        board[row][col] = boosters[row][col];
                    } else {
                        board[row][col] = EMPTY;
                    }
                }
            }
        }
        clearBoosterPlacementHints();
        return removed;
    }

    private boolean[][] copyMatches(boolean[][] matches) {
        boolean[][] result = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(matches[row], 0, result[row], 0, BOARD_SIZE);
        }
        return result;
    }

    private void copyBooleanGrid(boolean[][] source, boolean[][] target) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(source[row], 0, target[row], 0, BOARD_SIZE);
        }
    }

    private void copyIntGrid(int[][] source, int[][] target) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            System.arraycopy(source[row], 0, target[row], 0, BOARD_SIZE);
        }
    }

    private void clearPendingClear() {
        clearBooleanGrid(pendingCellsToClear);
        clearBooleanGrid(pendingMatches);
        clearIntGrid(pendingBoosters);
    }

    private void clearBlastEffects() {
        clearBooleanGrid(blastCells);
        clearBooleanGrid(blastHorizontalRockets);
        clearBooleanGrid(blastVerticalRockets);
        clearBooleanGrid(blastBombs);
    }

    private void clearBooleanGrid(boolean[][] grid) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                grid[row][col] = false;
            }
        }
    }

    private void clearIntGrid(int[][] grid) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                grid[row][col] = 0;
            }
        }
    }

    private void markRocketBlast(boolean[][] cellsToClear, int row, int col) {
        if (isHorizontalRocket(board[row][col])) {
            for (int clearCol = 0; clearCol < BOARD_SIZE; clearCol++) {
                cellsToClear[row][clearCol] = true;
            }
        } else {
            for (int clearRow = 0; clearRow < BOARD_SIZE; clearRow++) {
                cellsToClear[clearRow][col] = true;
            }
        }
    }

    private void markCrossBlast(boolean[][] cellsToClear, int row, int col) {
        blastHorizontalRockets[row][col] = true;
        blastVerticalRockets[row][col] = true;
        for (int clearCol = 0; clearCol < BOARD_SIZE; clearCol++) {
            cellsToClear[row][clearCol] = true;
        }
        for (int clearRow = 0; clearRow < BOARD_SIZE; clearRow++) {
            cellsToClear[clearRow][col] = true;
        }
    }

    private void markWholeBoard(boolean[][] cellsToClear) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                cellsToClear[row][col] = true;
            }
        }
    }

    private void markGemType(boolean[][] cellsToClear, int gemType) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (baseGemType(board[row][col]) == gemType) {
                    cellsToClear[row][col] = true;
                }
            }
        }
    }

    private void markBombBlast(boolean[][] cellsToClear, int row, int col) {
        for (int clearRow = row - 1; clearRow <= row + 1; clearRow++) {
            for (int clearCol = col - 1; clearCol <= col + 1; clearCol++) {
                if (isInside(clearRow, clearCol)) {
                    cellsToClear[clearRow][clearCol] = true;
                }
            }
        }
    }

    private int chooseBombBoosterPosition(boolean[][] matches) {
        if (isCornerMatchForMovedGem(matches, boosterPreferredRow, boosterPreferredCol)) {
            return boosterPreferredRow * BOARD_SIZE + boosterPreferredCol;
        }
        return findCornerIntersection(matches);
    }

    private int findCornerIntersection(boolean[][] matches) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            int runStart = 0;
            for (int col = 1; col <= BOARD_SIZE; col++) {
                boolean same = col < BOARD_SIZE && sameGemType(board[row][col], board[row][runStart]);
                if (!same) {
                    int runLength = col - runStart;
                    if (board[row][runStart] != EMPTY && runLength >= 3) {
                        int gemType = baseGemType(board[row][runStart]);
                        int intersection = findVerticalIntersectionInRun(matches, row, runStart, col, gemType);
                        if (intersection != -1) {
                            return intersection;
                        }
                    }
                    runStart = col;
                }
            }
        }
        return -1;
    }

    private int findVerticalIntersectionInRun(boolean[][] matches, int row, int runStart, int runEnd, int gemType) {
        for (int col = runStart; col < runEnd; col++) {
            int verticalStart = row;
            while (verticalStart > 0 && board[verticalStart - 1][col] != EMPTY && baseGemType(board[verticalStart - 1][col]) == gemType) {
                verticalStart--;
            }

            int verticalEnd = row + 1;
            while (verticalEnd < BOARD_SIZE && board[verticalEnd][col] != EMPTY && baseGemType(board[verticalEnd][col]) == gemType) {
                verticalEnd++;
            }

            if (verticalEnd - verticalStart >= 3 && matches[row][col]) {
                return row * BOARD_SIZE + col;
            }
        }
        return -1;
    }

    private boolean isCornerMatchForMovedGem(boolean[][] matches, int row, int col) {
        if (!isInside(row, col) || board[row][col] == EMPTY || !matches[row][col]) {
            return false;
        }

        int gemType = baseGemType(board[row][col]);
        for (int horizontalRow = 0; horizontalRow < BOARD_SIZE; horizontalRow++) {
            int horizontalStart = 0;
            for (int horizontalEnd = 1; horizontalEnd <= BOARD_SIZE; horizontalEnd++) {
                boolean sameHorizontal = horizontalEnd < BOARD_SIZE
                    && board[horizontalRow][horizontalStart] != EMPTY
                    && baseGemType(board[horizontalRow][horizontalStart]) == gemType
                    && sameGemType(board[horizontalRow][horizontalEnd], board[horizontalRow][horizontalStart]);
                if (sameHorizontal) {
                    continue;
                }

                if (isRunOfGemType(horizontalRow, horizontalStart, horizontalEnd, gemType)) {
                    boolean movedInHorizontal = row == horizontalRow && col >= horizontalStart && col < horizontalEnd;
                    if (hasIntersectingVerticalRun(gemType, horizontalRow, horizontalStart, horizontalEnd, row, col, movedInHorizontal)) {
                        return true;
                    }
                }
                horizontalStart = horizontalEnd;
            }
        }
        return false;
    }

    private boolean isRunOfGemType(int row, int runStart, int runEnd, int gemType) {
        return runEnd - runStart >= 3 && board[row][runStart] != EMPTY && baseGemType(board[row][runStart]) == gemType;
    }

    private boolean hasIntersectingVerticalRun(int gemType, int horizontalRow, int horizontalStart, int horizontalEnd, int movedRow, int movedCol, boolean movedInHorizontal) {
        for (int verticalCol = 0; verticalCol < BOARD_SIZE; verticalCol++) {
            int verticalStart = 0;
            for (int verticalEnd = 1; verticalEnd <= BOARD_SIZE; verticalEnd++) {
                boolean sameVertical = verticalEnd < BOARD_SIZE
                    && board[verticalStart][verticalCol] != EMPTY
                    && baseGemType(board[verticalStart][verticalCol]) == gemType
                    && sameGemType(board[verticalEnd][verticalCol], board[verticalStart][verticalCol]);
                if (sameVertical) {
                    continue;
                }

                boolean isVerticalRun = verticalEnd - verticalStart >= 3
                    && board[verticalStart][verticalCol] != EMPTY
                    && baseGemType(board[verticalStart][verticalCol]) == gemType;
                boolean intersectsHorizontal = horizontalRow >= verticalStart && horizontalRow < verticalEnd
                    && verticalCol >= horizontalStart && verticalCol < horizontalEnd;
                boolean movedInVertical = movedCol == verticalCol && movedRow >= verticalStart && movedRow < verticalEnd;
                if (isVerticalRun && intersectsHorizontal && (movedInHorizontal || movedInVertical)) {
                    return true;
                }
                verticalStart = verticalEnd;
            }
        }
        return false;
    }

    private int chooseBoosterCol(int row, int runStart, int runEnd) {
        if (boosterPreferredRow == row && boosterPreferredCol >= runStart && boosterPreferredCol < runEnd) {
            return boosterPreferredCol;
        }
        if (boosterFallbackRow == row && boosterFallbackCol >= runStart && boosterFallbackCol < runEnd) {
            return boosterFallbackCol;
        }
        return runStart + MathUtils.random(runEnd - runStart - 1);
    }

    private int chooseBoosterRow(int col, int runStart, int runEnd) {
        if (boosterPreferredCol == col && boosterPreferredRow >= runStart && boosterPreferredRow < runEnd) {
            return boosterPreferredRow;
        }
        if (boosterFallbackCol == col && boosterFallbackRow >= runStart && boosterFallbackRow < runEnd) {
            return boosterFallbackRow;
        }
        return runStart + MathUtils.random(runEnd - runStart - 1);
    }

    private void clearBoosterPlacementHints() {
        boosterPreferredRow = -1;
        boosterPreferredCol = -1;
        boosterFallbackRow = -1;
        boosterFallbackCol = -1;
    }

    private void resetBoard() {
        score = 0;
        moves = 0;
        levelCollected = 0;
        selectedRow = -1;
        selectedCol = -1;
        invalidFlash = 0f;
        statusText = levelGoalStatusText();
        animationState = AnimationState.IDLE;
        clearBoosterPlacementHints();
        clearPendingClear();
        clearBlastEffects();
        refillBoardKeepingProgress();
        syncDrawPositions();
    }

    private void startGame() {
        levelIndex = loadSavedLevelIndex();
        resetBoard();
        gameScreen = GameScreen.PLAYING;
        activeTouchPointer = -1;
        swipeHandled = false;
    }

    private void collectLevelTarget(int cell) {
        if (cell == EMPTY || isLevelComplete()) {
            return;
        }
        if (baseGemType(cell) == levelTargetType()) {
            levelCollected = Math.min(levelTargetCount(), levelCollected + 1);
        }
    }

    private boolean isLevelComplete() {
        return levelCollected >= levelTargetCount();
    }

    private void advanceLevel() {
        levelIndex++;
        saveLevelIndex();
        resetBoard();
        statusText = levelGoalStatusText();
    }

    private int levelTargetType() {
        int mixed = levelIndex * 1103515245 + 12345;
        return Math.floorMod(mixed >>> 16, GEM_TYPES);
    }

    private int levelTargetCount() {
        int base = LEVEL_TARGET_COUNTS[levelIndex % LEVEL_TARGET_COUNTS.length];
        return base + levelIndex * 3;
    }

    private String levelGoalStatusText() {
        return "Collect " + levelTargetCount() + " " + GEM_COLOR_NAMES[levelTargetType()];
    }

    private int loadSavedLevelIndex() {
        return Math.max(0, gamePreferences().getInteger(SAVED_LEVEL_KEY, 0));
    }

    private void saveLevelIndex() {
        Preferences preferences = gamePreferences();
        preferences.putInteger(SAVED_LEVEL_KEY, levelIndex);
        preferences.flush();
    }

    private Preferences gamePreferences() {
        return Gdx.app.getPreferences(GAME_PREFERENCES_NAME);
    }

    private void openMainMenu() {
        gameScreen = GameScreen.MENU;
        selectedRow = -1;
        selectedCol = -1;
        activeTouchPointer = -1;
        touchStartRow = -1;
        touchStartCol = -1;
        swipeHandled = false;
        restartAnimating = false;
        restartBoardRebuilt = false;
        restartAnimationTimer = 0f;
        animationState = AnimationState.IDLE;
    }

    private void refillBoardKeepingProgress() {
        do {
            fillFreshBoard();
        } while (!hasPossibleMove());
    }

    private void fillFreshBoard() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int gem;
                do {
                    gem = randomGem();
                } while (createsInitialMatch(row, col, gem));
                board[row][col] = gem;
            }
        }
    }

    private boolean hasPossibleMove() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (isPotentialMatch(row, col, row + 1, col) || isPotentialMatch(row, col, row, col + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPotentialMatch(int rowA, int colA, int rowB, int colB) {
        if (!isInside(rowB, colB)) {
            return false;
        }

        swap(rowA, colA, rowB, colB);
        boolean hasMatch = hasMatches(findMatches());
        swap(rowA, colA, rowB, colB);
        return hasMatch;
    }

    private boolean createsInitialMatch(int row, int col, int gem) {
        boolean horizontal = col >= 2 && sameGemType(board[row][col - 1], gem) && sameGemType(board[row][col - 2], gem);
        boolean vertical = row >= 2 && sameGemType(board[row - 1][col], gem) && sameGemType(board[row - 2][col], gem);
        return horizontal || vertical;
    }

    private void drawBackground() {
        batch.begin();
        batch.draw(background, 0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.16f, 0.21f, 0.32f, 0.25f);
        shapes.circle(Gdx.graphics.getWidth() * 0.18f, Gdx.graphics.getHeight() * 0.82f, Gdx.graphics.getWidth() * 0.18f, 64);
        shapes.setColor(0.42f, 0.16f, 0.34f, 0.18f);
        shapes.circle(Gdx.graphics.getWidth() * 0.82f, Gdx.graphics.getHeight() * 0.22f, Gdx.graphics.getWidth() * 0.22f, 64);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawMainMenu() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        float centerX = width * 0.5f;
        float panelWidth = menuPanelWidth();
        float panelHeight = menuPanelHeight();
        float panelX = menuPanelX();
        float panelY = menuPanelY();
        float radius = Math.max(18f, panelWidth * 0.04f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0f, 0f, 0f, 0.22f);
        shapes.rect(0f, 0f, width, height);

        shapes.setColor(0f, 0f, 0f, 0.28f);
        fillRoundedRect(panelX + 7f, panelY - 8f, panelWidth, panelHeight, radius);
        shapes.setColor(0.045f, 0.055f, 0.085f, 0.96f);
        fillRoundedRect(panelX, panelY, panelWidth, panelHeight, radius);
        shapes.setColor(0.15f, 0.28f, 0.40f, 0.30f);
        fillRoundedRect(panelX + 12f, panelY + 12f, panelWidth - 24f, panelHeight - 24f, radius * 0.70f);

        drawMenuButtonShape(playButtonX(), playButtonY(), menuButtonWidth(), menuActionButtonHeight(), 0.12f, 0.58f, 0.90f, isPlayButtonHovering());
        drawMenuButtonShape(settingsButtonX(), settingsButtonY(), menuButtonWidth(), menuActionButtonHeight(), 0.18f, 0.38f, 0.56f, isSettingsButtonHovering());
        drawMenuButtonShape(exitButtonX(), exitButtonY(), menuButtonWidth(), menuActionButtonHeight(), 0.18f, 0.22f, 0.32f, isExitButtonHovering());

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawCenteredTextFit("THREE IN ROW", centerX, panelY + panelHeight * 0.86f, menuTextScale(1.08f), menuPanelWidth() * 0.90f, Color.WHITE);
        drawMenuGemStrip(centerX, panelY + panelHeight * 0.73f);
        drawCenteredTextFit("Match crystals. Build boosters.", centerX, panelY + panelHeight * 0.61f, menuTextScale(0.62f), menuPanelWidth() * 0.86f, Color.valueOf("BFD7E8"));
        drawButtonTextInBox("PLAY", playButtonX(), playButtonY(), menuButtonWidth(), menuActionButtonHeight(), menuTextScale(1.05f), Color.WHITE);
        drawButtonTextInBox("SETTINGS", settingsButtonX(), settingsButtonY(), menuButtonWidth(), menuActionButtonHeight(), menuTextScale(0.80f), Color.WHITE);
        drawButtonTextInBox("EXIT", exitButtonX(), exitButtonY(), menuButtonWidth(), menuActionButtonHeight(), menuTextScale(0.90f), Color.valueOf("D9E5EF"));
    }

    private void drawSettingsMenu() {
        float width = Gdx.graphics.getWidth();
        float centerX = width * 0.5f;
        float panelWidth = menuPanelWidth();
        float panelHeight = menuPanelHeight();
        float panelX = menuPanelX();
        float panelY = menuPanelY();
        float radius = Math.max(18f, panelWidth * 0.04f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.22f);
        shapes.rect(0f, 0f, width, Gdx.graphics.getHeight());
        shapes.setColor(0f, 0f, 0f, 0.28f);
        fillRoundedRect(panelX + 7f, panelY - 8f, panelWidth, panelHeight, radius);
        shapes.setColor(0.045f, 0.055f, 0.085f, 0.96f);
        fillRoundedRect(panelX, panelY, panelWidth, panelHeight, radius);
        shapes.setColor(0.15f, 0.28f, 0.40f, 0.30f);
        fillRoundedRect(panelX + 12f, panelY + 12f, panelWidth - 24f, panelHeight - 24f, radius * 0.70f);
        for (int button = SETTINGS_BGM_DOWN; button <= SETTINGS_TRACK_NEXT; button++) {
            drawMenuButtonShape(settingsControlX(button), settingsControlY(button), settingsControlWidth(button), settingsControlHeight(), 0.12f, 0.35f, 0.55f, isSettingsControlHovering(button));
        }
        drawPlaylistRows();
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawCenteredTextFit("SETTINGS", centerX, panelY + panelHeight * 0.84f, menuTextScale(0.86f), menuPanelWidth() * 0.86f, Color.WHITE);
        drawCenteredTextFit(playlistStatusText(), centerX, panelY + panelHeight * 0.74f, menuTextScale(0.52f), menuPanelWidth() * 0.86f, Color.valueOf("8DCFFF"));
        drawPlaylistText();
        drawCenteredTextFit("BGM " + volumePercent(audio.musicVolume()), centerX, settingsControlY(SETTINGS_BGM_DOWN) + settingsControlHeight() * 1.28f, menuTextScale(0.56f), menuPanelWidth() * 0.80f, Color.valueOf("8DCFFF"));
        drawCenteredTextFit("DESTROY SFX " + volumePercent(audio.destroyVolume()), centerX, settingsControlY(SETTINGS_SFX_DOWN) + settingsControlHeight() * 1.28f, menuTextScale(0.50f), menuPanelWidth() * 0.86f, Color.valueOf("8DCFFF"));
        drawIconButton(minusIcon, settingsControlCenterX(SETTINGS_BGM_DOWN), settingsControlY(SETTINGS_BGM_DOWN) + settingsControlHeight() * 0.50f, settingsControlHeight() * 0.62f, Color.WHITE);
        drawIconButton(plusIcon, settingsControlCenterX(SETTINGS_BGM_UP), settingsControlY(SETTINGS_BGM_UP) + settingsControlHeight() * 0.50f, settingsControlHeight() * 0.62f, Color.WHITE);
        drawIconButton(minusIcon, settingsControlCenterX(SETTINGS_SFX_DOWN), settingsControlY(SETTINGS_SFX_DOWN) + settingsControlHeight() * 0.50f, settingsControlHeight() * 0.62f, Color.WHITE);
        drawIconButton(plusIcon, settingsControlCenterX(SETTINGS_SFX_UP), settingsControlY(SETTINGS_SFX_UP) + settingsControlHeight() * 0.50f, settingsControlHeight() * 0.62f, Color.WHITE);
        drawButtonTextFit("LOAD MUSIC", settingsControlCenterX(SETTINGS_LOAD_MUSIC), settingsControlTextY(SETTINGS_LOAD_MUSIC), menuTextScale(0.58f), settingsControlWidth(SETTINGS_LOAD_MUSIC) * 0.84f, Color.WHITE);
        drawButtonTextFit("BACK", settingsControlCenterX(SETTINGS_BACK), settingsControlTextY(SETTINGS_BACK), menuTextScale(0.68f), settingsControlWidth(SETTINGS_BACK) * 0.84f, Color.WHITE);
        drawButtonTextFit("PREVIOUS", settingsControlCenterX(SETTINGS_TRACK_PREV), settingsControlTextY(SETTINGS_TRACK_PREV), menuTextScale(0.42f), settingsControlWidth(SETTINGS_TRACK_PREV) * 0.82f, Color.WHITE);
        drawButtonTextFit("NEXT", settingsControlCenterX(SETTINGS_TRACK_NEXT), settingsControlTextY(SETTINGS_TRACK_NEXT), menuTextScale(0.50f), settingsControlWidth(SETTINGS_TRACK_NEXT) * 0.82f, Color.WHITE);
    }

    private void drawPlaylistRows() {
        float rowX = playlistRowX();
        float rowWidth = playlistRowWidth();
        float rowHeight = playlistRowHeight();
        for (int row = 0; row < SETTINGS_TRACK_ROWS; row++) {
            int track = settingsTrackScroll + row;
            float rowY = playlistRowY(row);
            boolean enabled = audio.isTrackEnabled(track);
            boolean hovering = isPlaylistRowHit(track, Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
            shapes.setColor(0.06f, 0.13f, 0.19f, hovering ? 0.96f : 0.78f);
            fillRoundedRect(rowX, rowY, rowWidth, rowHeight, rowHeight * 0.25f);
            shapes.setColor(0.16f, enabled ? 0.58f : 0.22f, enabled ? 0.86f : 0.30f, 0.92f);
            fillRoundedRect(rowX + rowHeight * 0.20f, rowY + rowHeight * 0.18f, rowHeight * 0.64f, rowHeight * 0.64f, rowHeight * 0.18f);
            if (track >= audio.trackCount()) {
                shapes.setColor(0.12f, 0.18f, 0.24f, 0.42f);
                fillRoundedRect(rowX, rowY, rowWidth, rowHeight, rowHeight * 0.25f);
            }
        }
    }

    private void drawPlaylistText() {
        for (int row = 0; row < SETTINGS_TRACK_ROWS; row++) {
            int track = settingsTrackScroll + row;
            if (track >= audio.trackCount()) {
                continue;
            }
            float rowY = playlistRowY(row);
            String state = audio.isTrackEnabled(track) ? "ON" : "OFF";
            String name = clippedTrackName(audio.trackName(track));
            Color color = audio.isTrackEnabled(track) ? Color.WHITE : Color.valueOf("8FA6B8");
            drawCenteredText(state, playlistRowX() + playlistRowHeight() * 0.55f, rowY + playlistRowHeight() * 0.72f, menuTextScale(0.48f), Color.WHITE);
            drawLeftText(name, playlistRowX() + playlistRowHeight() * 1.12f, rowY + playlistRowHeight() * 0.72f, menuTextScale(0.50f), color);
        }
    }

    private void drawMenuGemStrip(float centerX, float centerY) {
        float size = menuPanelHeight() * 0.105f;
        float gap = size * 0.72f;

        batch.begin();
        batch.setColor(1f, 1f, 1f, 0.94f);
        for (int i = 0; i < GEM_TYPES; i++) {
            float phase = menuTime * 1.7f + i * 0.85f;
            float y = centerY + MathUtils.sin(phase) * size * 0.13f;
            float x = centerX + (i - (GEM_TYPES - 1) * 0.5f) * gap - size * 0.5f;
            batch.draw(gemTextures[i], x, y - size * 0.5f, size, size);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawMenuButtonShape(float x, float y, float width, float height, float red, float green, float blue, boolean active) {
        drawSoftButton(x, y, width, height, red, green, blue, 0.36f, active);
    }

    private float menuTextScale(float multiplier) {
        return multiplier * MathUtils.clamp(Math.min(menuPanelWidth() / 330f, menuPanelHeight() / 520f), 3.35f, 4.85f);
    }

    private void handleMenuTap(int screenX, int worldY) {
        if (isPlayButtonHit(screenX, worldY)) {
            startGame();
            return;
        }
        if (isSettingsButtonHit(screenX, worldY)) {
            gameScreen = GameScreen.SETTINGS;
            return;
        }
        if (isExitButtonHit(screenX, worldY)) {
            Gdx.app.exit();
        }
    }

    private void handleSettingsTap(int screenX, int worldY) {
        int playlistTrack = playlistTrackAt(screenX, worldY);
        if (playlistTrack >= 0) {
            if (!audio.toggleTrackEnabled(playlistTrack)) {
                statusText = "Keep at least one track";
            }
        } else if (isSettingsControlHit(SETTINGS_BGM_DOWN, screenX, worldY)) {
            audio.decreaseMusicVolume();
        } else if (isSettingsControlHit(SETTINGS_BGM_UP, screenX, worldY)) {
            audio.increaseMusicVolume();
        } else if (isSettingsControlHit(SETTINGS_SFX_DOWN, screenX, worldY)) {
            audio.decreaseDestroyVolume();
        } else if (isSettingsControlHit(SETTINGS_SFX_UP, screenX, worldY)) {
            audio.increaseDestroyVolume();
        } else if (isSettingsControlHit(SETTINGS_LOAD_MUSIC, screenX, worldY)) {
            requestCustomMusic();
        } else if (isSettingsControlHit(SETTINGS_BACK, screenX, worldY)) {
            openMainMenu();
        } else if (isSettingsControlHit(SETTINGS_TRACK_PREV, screenX, worldY)) {
            settingsTrackScroll = Math.max(0, settingsTrackScroll - SETTINGS_TRACK_ROWS);
        } else if (isSettingsControlHit(SETTINGS_TRACK_NEXT, screenX, worldY)) {
            settingsTrackScroll = Math.min(maxSettingsTrackScroll(), settingsTrackScroll + SETTINGS_TRACK_ROWS);
        }
    }

    private void requestCustomMusic() {
        if (musicImportHandler == null) {
            statusText = "Music loading is unavailable";
            return;
        }
        musicImportHandler.requestMusicImport(new MusicImportCallback() {
            @Override
            public void onMusicImported(FileHandle file) {
                if (audio.addCustomTrack(file)) {
                    statusText = "Music added";
                } else {
                    statusText = "Music already in playlist";
                }
            }

            @Override
            public void onMusicImportFailed(String message) {
                statusText = message;
            }
        });
    }

    private boolean isSettingsControlHit(int button, int screenX, int worldY) {
        return screenX >= settingsControlX(button)
            && screenX <= settingsControlX(button) + settingsControlWidth(button)
            && worldY >= settingsControlY(button)
            && worldY <= settingsControlY(button) + settingsControlHeight();
    }

    private boolean isSettingsControlHovering(int button) {
        return isSettingsControlHit(button, Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private float settingsControlX(int button) {
        float centerX = Gdx.graphics.getWidth() * 0.5f;
        float gap = settingsControlHeight() * 0.32f;
        if (button == SETTINGS_TRACK_PREV) {
            return centerX - playlistRowWidth() * 0.5f;
        }
        if (button == SETTINGS_TRACK_NEXT) {
            return centerX + playlistRowWidth() * 0.5f - settingsControlWidth(button);
        }
        if (button == SETTINGS_BGM_DOWN || button == SETTINGS_SFX_DOWN) {
            return centerX - settingsControlWidth(button) - gap * 0.5f;
        }
        if (button == SETTINGS_BGM_UP || button == SETTINGS_SFX_UP) {
            return centerX + gap * 0.5f;
        }
        return centerX - settingsControlWidth(button) * 0.5f;
    }

    private float settingsControlY(int button) {
        float panelY = menuPanelY();
        float panelHeight = menuPanelHeight();
        if (button == SETTINGS_TRACK_PREV || button == SETTINGS_TRACK_NEXT) {
            return panelY + panelHeight * 0.620f;
        }
        if (button == SETTINGS_BGM_DOWN || button == SETTINGS_BGM_UP) {
            return panelY + panelHeight * 0.300f;
        }
        if (button == SETTINGS_SFX_DOWN || button == SETTINGS_SFX_UP) {
            return panelY + panelHeight * 0.175f;
        }
        if (button == SETTINGS_LOAD_MUSIC) {
            return panelY + panelHeight * 0.085f;
        }
        return panelY + panelHeight * 0.010f;
    }

    private float settingsControlWidth(int button) {
        if (button == SETTINGS_TRACK_PREV || button == SETTINGS_TRACK_NEXT) {
            return playlistRowWidth() * 0.42f;
        }
        if (button == SETTINGS_BACK || button == SETTINGS_LOAD_MUSIC) {
            return menuButtonWidth() * 0.66f;
        }
        return settingsControlHeight() * 1.34f;
    }

    private float settingsControlHeight() {
        return menuPanelHeight() * 0.074f;
    }

    private float settingsControlCenterX(int button) {
        return settingsControlX(button) + settingsControlWidth(button) * 0.5f;
    }

    private float settingsControlTextY(int button) {
        return settingsControlY(button) + settingsControlHeight() * 0.62f;
    }

    private float playlistRowX() {
        return Gdx.graphics.getWidth() * 0.5f - playlistRowWidth() * 0.5f;
    }

    private float playlistRowWidth() {
        return menuButtonWidth() * 0.82f;
    }

    private float playlistRowHeight() {
        return menuPanelHeight() * 0.058f;
    }

    private float playlistRowY(int row) {
        return menuPanelY() + menuPanelHeight() * 0.535f - row * playlistRowHeight() * 1.12f;
    }

    private int playlistTrackAt(int screenX, int worldY) {
        for (int row = 0; row < SETTINGS_TRACK_ROWS; row++) {
            int track = settingsTrackScroll + row;
            if (isPlaylistRowHit(track, screenX, worldY)) {
                return track;
            }
        }
        return -1;
    }

    private boolean isPlaylistRowHit(int track, int screenX, int worldY) {
        if (track < 0 || track >= audio.trackCount()) {
            return false;
        }
        int row = track - settingsTrackScroll;
        if (row < 0 || row >= SETTINGS_TRACK_ROWS) {
            return false;
        }
        return screenX >= playlistRowX()
            && screenX <= playlistRowX() + playlistRowWidth()
            && worldY >= playlistRowY(row)
            && worldY <= playlistRowY(row) + playlistRowHeight();
    }

    private int maxSettingsTrackScroll() {
        return Math.max(0, audio.trackCount() - SETTINGS_TRACK_ROWS);
    }

    private String playlistStatusText() {
        int totalTracks = audio.trackCount();
        int totalPages = Math.max(1, (totalTracks + SETTINGS_TRACK_ROWS - 1) / SETTINGS_TRACK_ROWS);
        int currentPage = Math.min(totalPages, settingsTrackScroll / SETTINGS_TRACK_ROWS + 1);
        return "PLAYLIST: " + audio.enabledTrackCount() + "/" + totalTracks + " ON  PAGE " + currentPage + "/" + totalPages;
    }

    private void drawBoardBackground() {
        float cell = cellSize();
        float boardX = boardX();
        float boardY = boardY();
        float boardPixels = cell * BOARD_SIZE;
        float panelPadding = Math.max(12f, cell * 0.18f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.28f);
        shapes.rect(boardX - panelPadding + 6f, boardY - panelPadding - 7f, boardPixels + panelPadding * 2f, boardPixels + panelPadding * 2f);
        shapes.setColor(0.075f, 0.09f, 0.14f, 0.96f);
        shapes.rect(boardX - panelPadding, boardY - panelPadding, boardPixels + panelPadding * 2f, boardPixels + panelPadding * 2f);
        shapes.setColor(0.18f, 0.22f, 0.32f, 0.95f);
        shapes.rect(boardX - panelPadding + 5f, boardY - panelPadding + 5f, boardPixels + panelPadding * 2f - 10f, boardPixels + panelPadding * 2f - 10f);

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                float shade = (row + col) % 2 == 0 ? 0.22f : 0.165f;
                shapes.setColor(shade, shade + 0.02f, shade + 0.075f, 0.92f);
                shapes.rect(boardX + col * cell + 3f, boardY + row * cell + 3f, cell - 6f, cell - 6f);
            }
        }

        if (invalidFlash > 0f) {
            shapes.setColor(1f, 0.18f, 0.23f, invalidFlash / INVALID_FLASH_TIME * 0.22f);
            shapes.rect(boardX, boardY, boardPixels, boardPixels);
        }

        if (selectedRow != -1) {
            shapes.setColor(1f, 1f, 1f, 0.09f);
            highlightCellFill(selectedRow + 1, selectedCol, cell, boardX, boardY);
            highlightCellFill(selectedRow - 1, selectedCol, cell, boardX, boardY);
            highlightCellFill(selectedRow, selectedCol + 1, cell, boardX, boardY);
            highlightCellFill(selectedRow, selectedCol - 1, cell, boardX, boardY);
        }
        shapes.end();

        if (selectedRow != -1) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 0.9f, 0.42f, 1f);
            Gdx.gl.glLineWidth(4f);
            shapes.rect(boardX + selectedCol * cell + 4f, boardY + selectedRow * cell + 4f, cell - 8f, cell - 8f);
            shapes.end();
            Gdx.gl.glLineWidth(1f);
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void highlightCellFill(int row, int col, float cell, float boardX, float boardY) {
        if (isInside(row, col)) {
            shapes.rect(boardX + col * cell + 8f, boardY + row * cell + 8f, cell - 16f, cell - 16f);
        }
    }

    private void drawGems() {
        float cell = cellSize();
        float padding = Math.max(5f, cell * 0.09f);
        float clearProgress = animationState == AnimationState.MATCH_CLEAR ? Math.min(1f, animationTimer / animationDuration) : 0f;
        float clearEased = Interpolation.smooth.apply(clearProgress);
        float boardX = boardX();
        float boardY = boardY();
        int scissorX = MathUtils.floor(boardX);
        int scissorY = MathUtils.floor(boardY);
        int scissorSize = MathUtils.ceil(cell * BOARD_SIZE);

        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(scissorX, scissorY, scissorSize, scissorSize);
        batch.begin();
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int gem = board[row][col];
                if (gem == EMPTY) {
                    continue;
                }
                boolean clearing = animationState == AnimationState.MATCH_CLEAR && pendingCellsToClear[row][col];
                float localPadding = clearing ? padding + cell * 0.18f * clearEased : padding;
                if (restartAnimating) {
                    float cellWave = restartCellWave(row, col);
                    if (restartBoardRebuilt) {
                        localPadding += cell * 0.30f * (1f - Interpolation.smooth.apply(cellWave));
                    } else {
                        localPadding += cell * 0.28f * Interpolation.smooth.apply(cellWave);
                    }
                }
                float size = cell - localPadding * 2f;
                if (clearing) {
                    batch.setColor(1f, 1f, 1f, 1f - clearProgress * 0.55f);
                } else if (restartAnimating) {
                    float cellWave = restartCellWave(row, col);
                    float alpha = restartBoardRebuilt ? cellWave : 1f - cellWave * 0.98f;
                    batch.setColor(1f, 1f, 1f, alpha);
                } else {
                    batch.setColor(Color.WHITE);
                }
                drawCellTexture(gem, boardX + drawCols[row][col] * cell + localPadding, boardY + drawRows[row][col] * cell + localPadding, size);
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawCellTexture(int gem, float x, float y, float size) {
        Texture texture = textureForCell(gem);
        if (isBooster(gem)) {
            float boosterScale = isRocket(gem) ? ROCKET_DRAW_SCALE : BOOSTER_DRAW_SCALE;
            float boosterSize = size * boosterScale;
            float boosterOffset = (boosterSize - size) * 0.5f;
            x -= boosterOffset;
            y -= boosterOffset;
            size = boosterSize;
            drawBoosterTextureGlow(gem, texture, x, y, size);
        }
        drawCellTextureRaw(gem, texture, x, y, size);
    }

    private void drawBoosterTextureGlow(int gem, Texture texture, float x, float y, float size) {
        Color previous = new Color(batch.getColor());
        float wave = (menuTime * 0.45f) % 1f;
        float delayedWave = (wave + 0.48f) % 1f;
        Color glow = boosterGlowColor(gem);
        float outlineSize = size * 1.10f;
        float outlineOffset = size * 0.055f;

        drawBoosterGlowWave(gem, texture, x, y, size, glow, previous.a, wave);
        drawBoosterGlowWave(gem, texture, x, y, size, glow, previous.a, delayedWave);
        batch.setColor(glow.r, glow.g, glow.b, previous.a * 0.42f);
        drawCellTextureRaw(gem, texture, x - outlineOffset, y, outlineSize);
        drawCellTextureRaw(gem, texture, x + outlineOffset, y, outlineSize);
        drawCellTextureRaw(gem, texture, x, y - outlineOffset, outlineSize);
        drawCellTextureRaw(gem, texture, x, y + outlineOffset, outlineSize);
        batch.setColor(1f, 1f, 1f, previous.a * 0.22f);
        drawCellTextureRaw(gem, texture, x - (outlineSize - size) * 0.5f, y - (outlineSize - size) * 0.5f, outlineSize);
        batch.setColor(previous);
    }

    private void drawBoosterGlowWave(int gem, Texture texture, float x, float y, float size, Color glow, float baseAlpha, float wave) {
        float eased = Interpolation.smooth.apply(wave);
        float waveSize = size * (1.22f + eased * 0.72f);
        float alpha = baseAlpha * (0.40f * (1f - wave));

        batch.setColor(glow.r, glow.g, glow.b, alpha);
        drawCellTextureRaw(gem, texture, x - (waveSize - size) * 0.5f, y - (waveSize - size) * 0.5f, waveSize);
    }

    private Color boosterGlowColor(int gem) {
        int type = baseGemType(gem);
        if (type == 0) {
            return Color.valueOf("2ED6FF");
        } else if (type == 1) {
            return Color.valueOf("45FF85");
        } else if (type == 2) {
            return Color.valueOf("FF3F61");
        } else if (type == 3) {
            return Color.valueOf("FFE052");
        } else if (type == 4) {
            return Color.valueOf("A96DFF");
        }
        return Color.valueOf("FF8B38");
    }

    private void drawCellTextureRaw(int gem, Texture texture, float x, float y, float size) {
        if (isHorizontalRocket(gem)) {
            batch.draw(
                texture,
                x,
                y,
                size * 0.5f,
                size * 0.5f,
                size,
                size,
                1f,
                1f,
                -90f,
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                false,
                false
            );
            return;
        }
        batch.draw(texture, x, y, size, size);
    }

    private float restartCellWave(int row, int col) {
        float progress = restartAnimationProgress();
        float phaseProgress = restartBoardRebuilt
            ? MathUtils.clamp((progress - 0.52f) / 0.48f, 0f, 1f)
            : MathUtils.clamp(progress / 0.52f, 0f, 1f);
        float eased = Interpolation.smooth.apply(phaseProgress);
        float center = (BOARD_SIZE - 1) * 0.5f;
        float maxDistance = BOARD_SIZE - 1f;
        float distance = Math.abs(row - center) + Math.abs(col - center);
        float normalizedDistance = distance / maxDistance;
        float delay = restartBoardRebuilt ? (1f - normalizedDistance) * 0.30f : normalizedDistance * 0.36f;
        return MathUtils.clamp((eased - delay) / (1f - delay), 0f, 1f);
    }

    private void drawBoosterEffects() {
        if (animationState != AnimationState.BOOSTER_BLAST && animationState != AnimationState.MATCH_CLEAR) {
            return;
        }

        float progress = Math.min(1f, animationTimer / animationDuration);
        float eased = Interpolation.smooth.apply(progress);
        float fade = 1f - progress;
        float cell = cellSize();
        float boardX = boardX();
        float boardY = boardY();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawBlastCellHighlights(cell, boardX, boardY, eased, fade);
        if (animationState == AnimationState.BOOSTER_BLAST) {
            drawRocketBeams(cell, boardX, boardY, eased, fade);
            drawBombWaves(cell, boardX, boardY, eased, fade);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawBlastCellHighlights(float cell, float boardX, float boardY, float eased, float fade) {
        float inset = MathUtils.lerp(cell * 0.32f, cell * 0.06f, eased);
        shapes.setColor(1f, 0.86f, 0.34f, 0.12f + fade * 0.20f);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (blastCells[row][col]) {
                    shapes.rect(boardX + col * cell + inset, boardY + row * cell + inset, cell - inset * 2f, cell - inset * 2f);
                }
            }
        }
    }

    private void drawRocketBeams(float cell, float boardX, float boardY, float eased, float fade) {
        float boardPixels = cell * BOARD_SIZE;
        float thickness = Math.max(8f, cell * 0.18f);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (blastHorizontalRockets[row][col]) {
                    float centerX = boardX + (col + 0.5f) * cell;
                    float centerY = boardY + (row + 0.5f) * cell;
                    float halfWidth = Math.max(cell * 0.45f, boardPixels * eased);
                    shapes.setColor(1f, 0.38f, 0.18f, 0.28f + fade * 0.25f);
                    shapes.rect(centerX - halfWidth, centerY - thickness * 0.5f, halfWidth * 2f, thickness);
                    shapes.setColor(1f, 0.96f, 0.66f, 0.48f + fade * 0.35f);
                    shapes.rect(centerX - halfWidth, centerY - thickness * 0.18f, halfWidth * 2f, thickness * 0.36f);
                }

                if (blastVerticalRockets[row][col]) {
                    float centerX = boardX + (col + 0.5f) * cell;
                    float centerY = boardY + (row + 0.5f) * cell;
                    float halfHeight = Math.max(cell * 0.45f, boardPixels * eased);
                    shapes.setColor(1f, 0.38f, 0.18f, 0.28f + fade * 0.25f);
                    shapes.rect(centerX - thickness * 0.5f, centerY - halfHeight, thickness, halfHeight * 2f);
                    shapes.setColor(1f, 0.96f, 0.66f, 0.48f + fade * 0.35f);
                    shapes.rect(centerX - thickness * 0.18f, centerY - halfHeight, thickness * 0.36f, halfHeight * 2f);
                }
            }
        }
    }

    private void drawBombWaves(float cell, float boardX, float boardY, float eased, float fade) {
        float radius = MathUtils.lerp(cell * 0.35f, cell * 1.85f, eased);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (blastBombs[row][col]) {
                    float centerX = boardX + (col + 0.5f) * cell;
                    float centerY = boardY + (row + 0.5f) * cell;
                    shapes.setColor(1f, 0.54f, 0.12f, 0.18f + fade * 0.22f);
                    shapes.circle(centerX, centerY, radius, 48);
                    shapes.setColor(1f, 0.95f, 0.58f, 0.32f + fade * 0.32f);
                    shapes.circle(centerX, centerY, Math.max(4f, radius * 0.32f), 32);
                }
            }
        }
    }

    private void drawHud() {
        String scoreText = String.valueOf(score);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawTopScorePanel();
        drawRestartButtonShape();
        drawHudMenuButtonShape();
        drawAudioControlShapes();
        drawScoreNumberShapes(scoreText);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawRestartButtonIcon();
        drawHudMenuButtonText();
        drawAudioControlText();
        drawScoreLabel();
        drawLevelGoal();
        drawHudStatus();
    }

    private void drawAudioControlShapes() {
        for (int button = AUDIO_PREV; button <= AUDIO_NEXT; button++) {
            drawSoftButton(audioButtonX(button), audioButtonY(), audioButtonWidth(), audioButtonHeight(), 0.10f, 0.20f, 0.31f, 0.24f, isAudioButtonHovering(button));
        }
    }

    private void drawAudioControlText() {
        drawIconButton(previousIcon, audioButtonCenterX(AUDIO_PREV), audioButtonY() + audioButtonHeight() * 0.50f, audioButtonHeight() * 0.66f, Color.WHITE);
        drawIconButton(nextIcon, audioButtonCenterX(AUDIO_NEXT), audioButtonY() + audioButtonHeight() * 0.50f, audioButtonHeight() * 0.66f, Color.WHITE);
        drawCenteredText(audio.trackLabel(), audioButtonControlsCenterX(), audioButtonY() - audioButtonHeight() * 0.12f, hudScale(0.42f), Color.valueOf("8DCFFF"));
    }

    private boolean isAudioButtonHit(int button, int screenX, int worldY) {
        return screenX >= audioButtonX(button)
            && screenX <= audioButtonX(button) + audioButtonWidth()
            && worldY >= audioButtonY()
            && worldY <= audioButtonY() + audioButtonHeight();
    }

    private boolean isAudioButtonHovering(int button) {
        return isAudioButtonHit(button, Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private float audioButtonX(int button) {
        float gap = audioButtonGap();
        float startX = restartButtonX() + restartButtonWidth() + menuButtonGap();
        if (button == AUDIO_NEXT) {
            return startX + audioButtonWidth() + gap;
        }
        return startX;
    }

    private float audioButtonY() {
        return restartButtonY();
    }

    private float audioButtonWidth() {
        return audioButtonHeight() * 1.30f;
    }

    private float audioButtonHeight() {
        return MathUtils.clamp(shortSide() * 0.14f, 78f * uiScale(), 120f * uiScale());
    }

    private float audioButtonGap() {
        return Math.max(6f, audioButtonHeight() * 0.18f);
    }

    private float audioButtonCenterX(int button) {
        return audioButtonX(button) + audioButtonWidth() * 0.5f;
    }

    private float audioButtonControlsCenterX() {
        return (audioButtonX(AUDIO_PREV) + audioButtonX(AUDIO_NEXT) + audioButtonWidth()) * 0.5f;
    }

    private String volumePercent(float volume) {
        return MathUtils.round(volume * 100f) + "%";
    }

    private void drawTopScorePanel() {
        float height = topHudHeight();
        shapes.setColor(0.03f, 0.04f, 0.065f, 0.72f);
        shapes.rect(0f, Gdx.graphics.getHeight() - height, Gdx.graphics.getWidth(), height);
        shapes.setColor(0.10f, 0.16f, 0.24f, 0.30f);
        fillRoundedRect(Gdx.graphics.getWidth() * 0.18f, Gdx.graphics.getHeight() - height + height * 0.12f, Gdx.graphics.getWidth() * 0.64f, height * 0.76f, height * 0.12f);
    }

    private void drawRestartButtonShape() {
        drawSoftButton(restartButtonX(), restartButtonY(), restartButtonWidth(), restartButtonHeight(), 0.11f, 0.22f, 0.34f, 0.28f, isRestartButtonHovering());
    }

    private void drawRestartButtonIcon() {
        float x = restartButtonX();
        float y = restartButtonY();
        float height = restartButtonHeight();
        float iconPadding = height * 0.08f;
        float iconSize = height - iconPadding * 2f;
        float centerX = x + height * 0.5f;
        float centerY = y + height * 0.5f;
        float rotation = restartAnimating ? -restartAnimationProgress() * 360f : 0f;

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(
            restartIcon,
            centerX - iconSize * 0.5f,
            centerY - iconSize * 0.5f,
            iconSize * 0.5f,
            iconSize * 0.5f,
            iconSize,
            iconSize,
            1f,
            1f,
            rotation,
            0,
            0,
            restartIcon.getWidth(),
            restartIcon.getHeight(),
            false,
            false
        );
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawHudMenuButtonShape() {
        drawSoftButton(hudMenuButtonX(), hudMenuButtonY(), hudMenuButtonWidth(), hudMenuButtonHeight(), 0.11f, 0.22f, 0.34f, 0.28f, isHudMenuButtonHovering());
    }

    private void drawHudMenuButtonText() {
        drawButtonTextInBox("MENU", hudMenuButtonX(), hudMenuButtonY(), hudMenuButtonWidth(), hudMenuButtonHeight(), hudScale(0.58f), Color.WHITE);
    }

    private float restartAnimationProgress() {
        if (!restartAnimating) {
            return 0f;
        }
        return Math.min(1f, restartAnimationTimer / RESTART_ANIMATION_TIME);
    }

    private void fillRoundedRect(float x, float y, float width, float height, float radius) {
        shapes.rect(x + radius, y, width - radius * 2f, height);
        shapes.rect(x, y + radius, width, height - radius * 2f);
        shapes.circle(x + radius, y + radius, radius, 32);
        shapes.circle(x + width - radius, y + radius, radius, 32);
        shapes.circle(x + radius, y + height - radius, radius, 32);
        shapes.circle(x + width - radius, y + height - radius, radius, 32);
    }

    private void drawSoftButton(float x, float y, float width, float height, float red, float green, float blue, float glowAlpha, boolean active) {
        float lift = active ? height * 0.035f : 0f;
        float radius = height * 0.30f;
        float glowPad = Math.max(4f, height * (active ? 0.11f : 0.08f));
        float innerPad = Math.max(4f, height * 0.07f);
        float activeBoost = active ? 0.10f : 0f;
        y += lift;

        shapes.setColor(0f, 0f, 0f, active ? 0.22f : 0.30f);
        fillRoundedRect(x + height * 0.09f, y - height * 0.12f, width, height, radius);
        shapes.setColor(
            Math.min(1f, red + activeBoost),
            Math.min(1f, green + activeBoost),
            Math.min(1f, blue + activeBoost),
            active ? glowAlpha + 0.16f : glowAlpha
        );
        fillRoundedRect(x - glowPad, y - glowPad, width + glowPad * 2f, height + glowPad * 2f, radius + glowPad);
        shapes.setColor(red * 0.32f, green * 0.35f, blue * 0.40f, 0.92f);
        fillRoundedRect(x - 1f, y - 1f, width + 2f, height + 2f, radius + 1f);
        shapes.setColor(
            Math.min(1f, red * 0.52f + activeBoost),
            Math.min(1f, green * 0.56f + activeBoost),
            Math.min(1f, blue * 0.62f + activeBoost),
            0.98f
        );
        fillRoundedRect(x, y, width, height, radius);
        shapes.setColor(
            Math.min(1f, red + 0.10f + activeBoost),
            Math.min(1f, green + 0.10f + activeBoost),
            Math.min(1f, blue + 0.10f + activeBoost),
            0.96f
        );
        fillRoundedRect(x + innerPad, y + innerPad, width - innerPad * 2f, height - innerPad * 2f, Math.max(1f, radius - innerPad));
    }

    private void drawCenteredText(String text, float centerX, float baselineY, float scale, Color color) {
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        batch.begin();
        font.setColor(color);
        font.draw(batch, text, centerX - glyphLayout.width * 0.5f, baselineY);
        batch.end();
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private void drawCenteredTextFit(String text, float centerX, float baselineY, float scale, float maxWidth, Color color) {
        drawCenteredText(text, centerX, baselineY, fittedTextScale(text, scale, maxWidth), color);
    }

    private void drawButtonTextFit(String text, float centerX, float baselineY, float scale, float maxWidth, Color color) {
        float fittedScale = fittedTextScale(text, scale, maxWidth);
        drawCenteredText(text, centerX + 2f, baselineY - 2f, fittedScale, Color.valueOf("102034"));
        drawCenteredText(text, centerX, baselineY, fittedScale, color);
    }

    private void drawButtonTextInBox(String text, float x, float y, float width, float height, float scale, Color color) {
        float fittedScale = fittedTextScale(text, scale, width * 0.82f);
        float baselineY = y + (height + textHeight(text, fittedScale)) * 0.5f;
        drawCenteredText(text, x + width * 0.5f + 2f, baselineY - 2f, fittedScale, Color.valueOf("102034"));
        drawCenteredText(text, x + width * 0.5f, baselineY, fittedScale, color);
    }

    private float fittedTextScale(String text, float scale, float maxWidth) {
        if (maxWidth <= 0f) {
            return scale;
        }
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        float fittedScale = glyphLayout.width > maxWidth ? scale * maxWidth / glyphLayout.width : scale;
        font.getData().setScale(1f);
        return fittedScale;
    }

    private float textHeight(String text, float scale) {
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        float height = glyphLayout.height;
        font.getData().setScale(1f);
        return height;
    }

    private void drawIconButton(Texture icon, float centerX, float centerY, float size, Color color) {
        if (icon == null) {
            return;
        }
        float width = size;
        float height = size;
        float ratio = (float) icon.getWidth() / Math.max(1f, icon.getHeight());
        if (ratio > 1f) {
            height = size / ratio;
        } else {
            width = size * ratio;
        }
        batch.begin();
        batch.setColor(color);
        batch.draw(icon, centerX - width * 0.5f, centerY - height * 0.5f, width, height);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLeftText(String text, float x, float baselineY, float scale, Color color) {
        font.getData().setScale(scale);
        batch.begin();
        font.setColor(color);
        font.draw(batch, text, x, baselineY);
        batch.end();
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private String clippedTrackName(String name) {
        int maxLength = MathUtils.clamp((int)(playlistRowWidth() / Math.max(1f, menuTextScale(0.38f) * 10f)), 10, 28);
        if (name.length() <= maxLength) {
            return name;
        }
        return name.substring(0, Math.max(1, maxLength - 3)) + "...";
    }

    private void drawButtonText(String text, float centerX, float baselineY, float scale, Color color) {
        drawCenteredText(text, centerX + 2f, baselineY - 2f, scale, Color.valueOf("102034"));
        drawCenteredText(text, centerX, baselineY, scale, color);
    }

    private void drawScoreNumberShapes(String text) {
        float scale = uiScale();
        float digitHeight = MathUtils.clamp(topHudHeight() * 0.36f, 42f * scale, 78f * scale);
        float digitWidth = digitHeight * 0.58f;
        float gap = digitHeight * 0.16f;
        float totalWidth = text.length() * digitWidth + Math.max(0, text.length() - 1) * gap;
        float startX = (Gdx.graphics.getWidth() - totalWidth) * 0.5f;
        float y = Gdx.graphics.getHeight() - topHudHeight() * 0.74f;
        float badgePadX = digitHeight * 0.42f;
        float badgePadTop = digitHeight * 0.46f;
        float badgePadBottom = digitHeight * 0.24f;
        float badgeX = startX - badgePadX;
        float badgeY = y - badgePadBottom;
        float badgeWidth = totalWidth + badgePadX * 2f;
        float badgeHeight = digitHeight + badgePadTop + badgePadBottom;
        float badgeRadius = Math.min(badgeHeight * 0.28f, 28f);

        shapes.setColor(0f, 0f, 0f, 0.30f);
        fillRoundedRect(badgeX + 5f, badgeY - 6f, badgeWidth, badgeHeight, badgeRadius);
        shapes.setColor(0.10f, 0.45f, 0.82f, 0.26f);
        fillRoundedRect(badgeX - 5f, badgeY - 5f, badgeWidth + 10f, badgeHeight + 10f, badgeRadius + 5f);
        shapes.setColor(0.035f, 0.055f, 0.09f, 0.95f);
        fillRoundedRect(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius);
        shapes.setColor(0.10f, 0.22f, 0.34f, 0.92f);
        fillRoundedRect(badgeX + 5f, badgeY + 5f, badgeWidth - 10f, badgeHeight - 10f, Math.max(1f, badgeRadius - 5f));

        shapes.setColor(0.04f, 0.08f, 0.13f, 0.64f);
        for (int i = 0; i < text.length(); i++) {
            drawScoreDigit(text.charAt(i), startX + i * (digitWidth + gap) + 4f, y - 4f, digitWidth, digitHeight);
        }
        shapes.setColor(0.16f, 0.64f, 1f, 0.34f);
        for (int i = 0; i < text.length(); i++) {
            drawScoreDigit(text.charAt(i), startX + i * (digitWidth + gap), y, digitWidth, digitHeight);
        }
        shapes.setColor(0.91f, 0.98f, 1f, 0.98f);
        for (int i = 0; i < text.length(); i++) {
            drawScoreDigit(text.charAt(i), startX + i * (digitWidth + gap), y, digitWidth, digitHeight);
        }
    }

    private void drawScoreLabel() {
        float scale = uiScale();
        float digitHeight = MathUtils.clamp(topHudHeight() * 0.36f, 42f * scale, 78f * scale);
        float centerX = Gdx.graphics.getWidth() * 0.5f;
        float digitY = Gdx.graphics.getHeight() - topHudHeight() * 0.74f;
        float labelY = digitY + digitHeight + digitHeight * 0.34f;
        float textScale = hudScale(0.36f);

        drawCenteredText("SCORE", centerX + 1f, labelY - 1f, textScale, Color.valueOf("06121E"));
        drawCenteredText("SCORE", centerX, labelY, textScale, Color.valueOf("78C8FF"));
    }

    private void drawLevelGoal() {
        float height = topHudHeight();
        float top = Gdx.graphics.getHeight();
        float iconSize = MathUtils.clamp(height * 0.58f, 76f * uiScale(), 118f * uiScale());
        float leftX = Gdx.graphics.getWidth() * 0.045f;
        float baseline = top - height * 0.45f;
        float textScale = hudScale(1.28f);

        drawCenteredText("LEVEL " + (levelIndex + 1), leftX + iconSize * 1.65f, top - height * 0.16f, hudScale(0.82f), Color.valueOf("8DCFFF"));

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(gemTextures[levelTargetType()], leftX, baseline - iconSize * 0.72f, iconSize, iconSize);
        batch.end();

        String goalText = levelCollected + "/" + levelTargetCount();
        drawCenteredText(goalText, leftX + iconSize * 2.45f, baseline, textScale, Color.WHITE);
    }

    private void drawHudStatus() {
        float height = topHudHeight();
        float top = Gdx.graphics.getHeight();
        float rightX = Gdx.graphics.getWidth() * 0.88f;
        drawCenteredText("MOVES " + moves, rightX, top - height * 0.20f, hudScale(0.34f), Color.valueOf("8DCFFF"));
    }

    private void drawScoreDigit(char digit, float x, float y, float width, float height) {
        boolean top = false;
        boolean upperLeft = false;
        boolean upperRight = false;
        boolean middle = false;
        boolean lowerLeft = false;
        boolean lowerRight = false;
        boolean bottom = false;

        if (digit == '0') {
            top = upperLeft = upperRight = lowerLeft = lowerRight = bottom = true;
        } else if (digit == '1') {
            upperRight = lowerRight = true;
        } else if (digit == '2') {
            top = upperRight = middle = lowerLeft = bottom = true;
        } else if (digit == '3') {
            top = upperRight = middle = lowerRight = bottom = true;
        } else if (digit == '4') {
            upperLeft = upperRight = middle = lowerRight = true;
        } else if (digit == '5') {
            top = upperLeft = middle = lowerRight = bottom = true;
        } else if (digit == '6') {
            top = upperLeft = middle = lowerLeft = lowerRight = bottom = true;
        } else if (digit == '7') {
            top = upperRight = lowerRight = true;
        } else if (digit == '8') {
            top = upperLeft = upperRight = middle = lowerLeft = lowerRight = bottom = true;
        } else if (digit == '9') {
            top = upperLeft = upperRight = middle = lowerRight = bottom = true;
        }

        float thickness = Math.max(5f, height * 0.13f);
        if (top) drawHorizontalSegment(x, y + height - thickness, width, thickness);
        if (middle) drawHorizontalSegment(x, y + (height - thickness) * 0.5f, width, thickness);
        if (bottom) drawHorizontalSegment(x, y, width, thickness);
        if (upperLeft) drawVerticalSegment(x, y + height * 0.5f, thickness, height * 0.5f);
        if (upperRight) drawVerticalSegment(x + width - thickness, y + height * 0.5f, thickness, height * 0.5f);
        if (lowerLeft) drawVerticalSegment(x, y, thickness, height * 0.5f);
        if (lowerRight) drawVerticalSegment(x + width - thickness, y, thickness, height * 0.5f);
    }

    private void drawHorizontalSegment(float x, float y, float width, float thickness) {
        shapes.rect(x + thickness * 0.5f, y, width - thickness, thickness);
        shapes.circle(x + thickness * 0.5f, y + thickness * 0.5f, thickness * 0.5f, 16);
        shapes.circle(x + width - thickness * 0.5f, y + thickness * 0.5f, thickness * 0.5f, 16);
    }

    private void drawVerticalSegment(float x, float y, float thickness, float height) {
        shapes.rect(x, y + thickness * 0.5f, thickness, height - thickness);
        shapes.circle(x + thickness * 0.5f, y + thickness * 0.5f, thickness * 0.5f, 16);
        shapes.circle(x + thickness * 0.5f, y + height - thickness * 0.5f, thickness * 0.5f, 16);
    }

    private Texture textureForCell(int cell) {
        int gemType = baseGemType(cell);
        if (isHorizontalRocket(cell)) {
            return rocketHorizontalTextures[gemType];
        }
        if (isVerticalRocket(cell)) {
            return rocketVerticalTextures[gemType];
        }
        if (isBomb(cell)) {
            return bombTextures[gemType];
        }
        if (isLightning(cell)) {
            return lightningTextures[gemType];
        }
        return gemTextures[gemType];
    }

    private int screenToCol(int screenX) {
        return MathUtils.floor((screenX - boardX()) / cellSize());
    }

    private int screenToRow(int screenY) {
        int worldY = Gdx.graphics.getHeight() - screenY;
        return MathUtils.floor((worldY - boardY()) / cellSize());
    }

    private boolean isInside(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean areAdjacent(int rowA, int colA, int rowB, int colB) {
        return Math.abs(rowA - rowB) + Math.abs(colA - colB) == 1;
    }

    private void swap(int rowA, int colA, int rowB, int colB) {
        int temp = board[rowA][colA];
        board[rowA][colA] = board[rowB][colB];
        board[rowB][colB] = temp;
    }

    private int randomGem() {
        return MathUtils.random(GEM_TYPES - 1);
    }

    private Texture loadUiTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        background.dispose();
        restartIcon.dispose();
        plusIcon.dispose();
        minusIcon.dispose();
        nextIcon.dispose();
        previousIcon.dispose();
        audio.dispose();
        for (Texture texture : gemTextures) {
            texture.dispose();
        }
        for (Texture texture : rocketHorizontalTextures) {
            if (texture != null && !isTextureInArray(texture, rocketVerticalTextures)) {
                texture.dispose();
            }
        }
        for (Texture texture : rocketVerticalTextures) {
            texture.dispose();
        }
        for (Texture texture : bombTextures) {
            texture.dispose();
        }
        for (Texture texture : lightningTextures) {
            texture.dispose();
        }
    }

    private boolean isTextureInArray(Texture texture, Texture[] textures) {
        for (Texture existing : textures) {
            if (texture == existing) {
                return true;
            }
        }
        return false;
    }
}
