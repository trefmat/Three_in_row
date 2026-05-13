package io.trefmat.three_in_row;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final int BOARD_SIZE = 8;
    private static final int GEM_TYPES = 6;
    private static final int EMPTY = -1;
    private static final int ROCKET_HORIZONTAL = 100;
    private static final int ROCKET_VERTICAL = 200;
    private static final int BOMB = 300;
    private static final int LIGHTNING = 400;
    private static final String[] GEM_COLOR_NAMES = {"blue", "green", "red", "yellow", "purple", "orange"};
    private static final float INVALID_FLASH_TIME = 0.25f;
    private static final float SWAP_TIME = 0.18f;
    private static final float FALL_TIME = 0.28f;
    private static final float INVALID_SWAP_TIME = 0.13f;
    private static final float MATCH_CLEAR_TIME = 0.22f;
    private static final float BOOSTER_BLAST_TIME = 0.36f;
    private static final float RESTART_ANIMATION_TIME = 1.05f;
    private static final float MENU_BUTTON_GAP = 18f;
    private static final float ROCKET_DRAW_SCALE = 1.25f;

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
    private Texture background;
    private Texture restartIcon;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private int score;
    private int moves;
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
    private int pendingRemoved;
    private int touchStartX;
    private int touchStartY;
    private int touchStartRow = -1;
    private int touchStartCol = -1;
    private int activeTouchPointer = -1;
    private boolean swipeHandled;
    private boolean restartAnimating;
    private boolean restartBoardRebuilt;
    private float restartAnimationTimer;

    private enum GameScreen {
        MENU,
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

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(1f);
        glyphLayout = new GlyphLayout();
        background = createBackgroundTexture();
        restartIcon = new Texture(Gdx.files.internal("restart.png"));
        restartIcon.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

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

        if (gameScreen == GameScreen.MENU) {
            Gdx.gl.glClearColor(0.045f, 0.05f, 0.075f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            drawBackground();
            drawMainMenu();
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
        if (restartAnimating) {
            return;
        }

        if (animationState != AnimationState.IDLE) {
            return;
        }

        int worldY = Gdx.graphics.getHeight() - screenY;
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

        if (!swipeHandled) {
            handleBoardTap(screenX, screenY);
        }
        activeTouchPointer = -1;
        touchStartRow = -1;
        touchStartCol = -1;
        swipeHandled = false;
    }

    private void handleSwipeDrag(int screenX, int screenY) {
        if (gameScreen == GameScreen.MENU) {
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

        if (!hasPossibleMove()) {
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
        score += removed * 10;
        clearBlastEffects();
        collapseAndFillAnimated();
        beginAnimation(AnimationState.FALLING, FALL_TIME);
        statusText = "Crystals falling";
    }

    private void finishMatchClear() {
        int removed = applyPendingClear();
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
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (!matches[row][col]) {
                    continue;
                }
                if (isBooster(board[row][col])) {
                    markBoosterBlast(cellsToClear, row, col);
                    activatedBooster = true;
                }
            }
        }

        if (activatedBooster) {
            copyBooleanGrid(cellsToClear, pendingCellsToClear);
            copyBooleanGrid(matches, pendingMatches);
            copyIntGrid(boosters, pendingBoosters);
            copyBooleanGrid(cellsToClear, blastCells);
            pendingRemoved = countCells(cellsToClear);
            clearBoosterPlacementHints();
            statusText = "Booster blast";
            beginAnimation(AnimationState.BOOSTER_BLAST, BOOSTER_BLAST_TIME);
            return -1;
        }

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        pendingRemoved = countCells(cellsToClear);
        clearBoosterPlacementHints();
        statusText = "Match cleared";
        beginAnimation(AnimationState.MATCH_CLEAR, MATCH_CLEAR_TIME);
        return -1;
    }

    private void beginBoosterSwapBlast() {
        boolean[][] cellsToClear = new boolean[BOARD_SIZE][BOARD_SIZE];
        boolean[][] matches = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[][] boosters = new int[BOARD_SIZE][BOARD_SIZE];
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
            markBoosterBlast(cellsToClear, swapRowA, swapColA);
            markBoosterBlast(cellsToClear, swapRowB, swapColB);
        }

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        pendingRemoved = countCells(cellsToClear);
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

        copyBooleanGrid(cellsToClear, pendingCellsToClear);
        copyBooleanGrid(matches, pendingMatches);
        copyIntGrid(boosters, pendingBoosters);
        copyBooleanGrid(cellsToClear, blastCells);
        pendingRemoved = countCells(cellsToClear);
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

    private void markBoosterBlast(boolean[][] cellsToClear, int row, int col) {
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
            for (int col = 0; col < BOARD_SIZE; col++) {
                result[row][col] = matches[row][col];
            }
        }
        return result;
    }

    private void copyBooleanGrid(boolean[][] source, boolean[][] target) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                target[row][col] = source[row][col];
            }
        }
    }

    private void copyIntGrid(int[][] source, int[][] target) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                target[row][col] = source[row][col];
            }
        }
    }

    private int countCells(boolean[][] cells) {
        int count = 0;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (cells[row][col]) {
                    count++;
                }
            }
        }
        return count;
    }

    private void clearPendingClear() {
        clearBooleanGrid(pendingCellsToClear);
        clearBooleanGrid(pendingMatches);
        clearIntGrid(pendingBoosters);
        pendingRemoved = 0;
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

    private void collapseColumns() {
        for (int col = 0; col < BOARD_SIZE; col++) {
            int writeRow = 0;
            for (int row = 0; row < BOARD_SIZE; row++) {
                if (board[row][col] != EMPTY) {
                    board[writeRow][col] = board[row][col];
                    if (writeRow != row) {
                        board[row][col] = EMPTY;
                    }
                    writeRow++;
                }
            }
        }
    }

    private void fillEmptyCells() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (board[row][col] == EMPTY) {
                    board[row][col] = randomGem();
                }
            }
        }
    }

    private void resetBoard() {
        score = 0;
        moves = 0;
        selectedRow = -1;
        selectedCol = -1;
        invalidFlash = 0f;
        statusText = "Pick a crystal";
        animationState = AnimationState.IDLE;
        clearBoosterPlacementHints();
        clearPendingClear();
        clearBlastEffects();
        refillBoardKeepingProgress();
        syncDrawPositions();
    }

    private void startGame() {
        resetBoard();
        gameScreen = GameScreen.PLAYING;
        activeTouchPointer = -1;
        swipeHandled = false;
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
        drawMenuButtonShape(exitButtonX(), exitButtonY(), menuButtonWidth(), menuActionButtonHeight(), 0.18f, 0.22f, 0.32f, isExitButtonHovering());

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawMenuGemStrip(centerX, panelY + panelHeight * 0.65f);
        drawCenteredText("THREE IN ROW", centerX, panelY + panelHeight * 0.83f, MathUtils.clamp(width / 660f, 0.88f, 1.28f), Color.WHITE);
        drawCenteredText("Match crystals. Build boosters.", centerX, panelY + panelHeight * 0.52f, hudScale(0.86f), Color.valueOf("BFD7E8"));
        drawButtonText("PLAY", centerX, playButtonY() + menuActionButtonHeight() * 0.62f, hudScale(1.18f), Color.WHITE);
        drawButtonText("EXIT", centerX, exitButtonY() + menuActionButtonHeight() * 0.62f, hudScale(0.96f), Color.valueOf("D9E5EF"));
    }

    private void drawMenuGemStrip(float centerX, float centerY) {
        float size = MathUtils.clamp(Gdx.graphics.getWidth() * 0.075f, 42f, 58f);
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

    private void handleMenuTap(int screenX, int worldY) {
        if (isPlayButtonHit(screenX, worldY)) {
            startGame();
            return;
        }
        if (isExitButtonHit(screenX, worldY)) {
            Gdx.app.exit();
        }
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
                drawCellTexture(gem, boardX() + drawCols[row][col] * cell + localPadding, boardY() + drawRows[row][col] * cell + localPadding, size);
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawCellTexture(int gem, float x, float y, float size) {
        Texture texture = textureForCell(gem);
        if (isRocket(gem)) {
            float rocketSize = size * ROCKET_DRAW_SCALE;
            float rocketOffset = (rocketSize - size) * 0.5f;
            x -= rocketOffset;
            y -= rocketOffset;
            size = rocketSize;
        }
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
        drawScoreNumberShapes(scoreText);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        drawRestartButtonIcon();
        drawHudMenuButtonText();
        drawScoreLabel(scoreText);
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
        drawButtonText(
            "MENU",
            hudMenuButtonX() + hudMenuButtonWidth() * 0.5f,
            hudMenuButtonY() + hudMenuButtonHeight() * 0.58f,
            hudScale(0.86f),
            Color.WHITE
        );
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

    private void drawButtonText(String text, float centerX, float baselineY, float scale, Color color) {
        drawCenteredText(text, centerX + 2f, baselineY - 2f, scale, Color.valueOf("102034"));
        drawCenteredText(text, centerX, baselineY, scale, color);
    }

    private void drawScoreNumberShapes(String text) {
        float digitHeight = MathUtils.clamp(topHudHeight() * 0.36f, 50f, 78f);
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

    private void drawScoreLabel(String scoreText) {
        float digitHeight = MathUtils.clamp(topHudHeight() * 0.36f, 50f, 78f);
        float digitWidth = digitHeight * 0.58f;
        float gap = digitHeight * 0.16f;
        float totalWidth = scoreText.length() * digitWidth + Math.max(0, scoreText.length() - 1) * gap;
        float centerX = Gdx.graphics.getWidth() * 0.5f;
        float digitY = Gdx.graphics.getHeight() - topHudHeight() * 0.74f;
        float labelY = digitY + digitHeight + digitHeight * 0.34f;
        float scale = hudScale(0.62f);

        drawCenteredText("SCORE", centerX + 1f, labelY - 1f, scale, Color.valueOf("06121E"));
        drawCenteredText("SCORE", centerX, labelY, scale, Color.valueOf("78C8FF"));
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

    private Texture createBackgroundTexture() {
        int width = 16;
        int height = 256;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        Color top = Color.valueOf("171B2E");
        Color bottom = Color.valueOf("07131A");

        for (int y = 0; y < height; y++) {
            float t = (float) y / (height - 1);
            Color rowColor = new Color(bottom).lerp(top, t);
            pixmap.setColor(rowColor);
            pixmap.drawLine(0, y, width, y);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture loadGemTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private Texture loadBombTexture(String colorName, Color fallbackColor) {
        Texture texture = loadTextureMatching(
            new String[] {"bombs", "bomb"},
            new String[] {colorName},
            new String[] {"bomb"}
        );
        if (texture != null) {
            return texture;
        }
        return createBombTexture(fallbackColor);
    }

    private Texture loadRocketTexture(String colorName, Color fallbackColor, boolean horizontal) {
        String[] orientationTokens = horizontal
            ? new String[] {"horizontal", "horiz", "row", "h"}
            : new String[] {"vertical", "vert", "column", "v"};
        Texture texture = loadTextureMatching(new String[] {"rockets", "rocket"}, new String[] {colorName}, orientationTokens);
        if (texture != null) {
            return texture;
        }
        if (!horizontal) {
            texture = loadTextureMatching(new String[] {"rockets", "rocket"}, new String[] {colorName}, new String[] {"rocket"});
            if (texture != null) {
                return texture;
            }
        }
        return createRocketTexture(fallbackColor, horizontal);
    }

    private Texture loadRocketTexture(String colorName, Color fallbackColor) {
        Texture texture = loadTextureMatching(
            new String[] {"rockets", "rocket"},
            new String[] {colorName},
            new String[] {"vertical", "vert", "column", "v", "rocket"}
        );
        if (texture != null) {
            return texture;
        }
        texture = loadTextureMatching(new String[] {"rockets", "rocket"}, new String[] {colorName}, new String[] {"rocket"});
        if (texture != null) {
            return texture;
        }
        return createRocketTexture(fallbackColor, false);
    }

    private Texture loadLightningTexture(String colorName, Color fallbackColor) {
        Texture texture = loadTextureMatching(
            new String[] {"lightnings", "lightning"},
            new String[] {colorName},
            new String[] {"lightning", "bolt"}
        );
        if (texture != null) {
            return texture;
        }
        return createLightningTexture(fallbackColor);
    }

    private Texture loadTextureMatching(String[] directories, String[] requiredTokens, String[] optionalTokens) {
        for (String directory : directories) {
            FileHandle folder = Gdx.files.internal(directory);
            if (!folder.exists() || !folder.isDirectory()) {
                continue;
            }
            for (FileHandle file : folder.list()) {
                String name = file.name().toLowerCase();
                if (!name.endsWith(".png")) {
                    continue;
                }
                if (containsAllTokens(name, requiredTokens) && containsAnyToken(name, optionalTokens)) {
                    Texture texture = new Texture(file);
                    texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    return texture;
                }
            }
        }
        return null;
    }

    private boolean containsAllTokens(String name, String[] tokens) {
        for (String token : tokens) {
            if (!name.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAnyToken(String name, String[] tokens) {
        for (String token : tokens) {
            if (name.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Texture createGemTexture(Color baseColor, int gemType) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        Color dark = new Color(baseColor).lerp(Color.BLACK, 0.35f);
        Color light = new Color(baseColor).lerp(Color.WHITE, 0.42f);
        Color shine = new Color(baseColor).lerp(Color.WHITE, 0.78f);

        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        pixmap.setColor(0f, 0f, 0f, 0.28f);
        pixmap.fillCircle(68, 72, 52);

        if (gemType == 0) {
            drawDiamondGem(pixmap, baseColor, dark, light);
        } else if (gemType == 1) {
            drawRoundGem(pixmap, baseColor, dark, light);
        } else if (gemType == 2) {
            drawSquareGem(pixmap, baseColor, dark, light);
        } else if (gemType == 3) {
            drawHexGem(pixmap, baseColor, dark, light);
        } else if (gemType == 4) {
            drawTriangleGem(pixmap, baseColor, dark, light);
        } else {
            drawShardGem(pixmap, baseColor, dark, light);
        }

        pixmap.setColor(shine);
        pixmap.fillCircle(47, 34, 9);
        pixmap.setColor(1f, 1f, 1f, 0.64f);
        pixmap.fillCircle(43, 30, 4);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture createRocketTexture(Color baseColor, boolean horizontal) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        Color dark = new Color(baseColor).lerp(Color.BLACK, 0.38f);
        Color light = new Color(baseColor).lerp(Color.WHITE, 0.5f);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        pixmap.setColor(0f, 0f, 0f, 0.28f);
        pixmap.fillCircle(68, 72, 50);

        if (horizontal) {
            pixmap.setColor(dark);
            pixmap.fillRectangle(24, 45, 68, 38);
            pixmap.fillTriangle(92, 38, 118, 64, 92, 90);
            pixmap.fillTriangle(24, 45, 8, 32, 24, 64);
            pixmap.fillTriangle(24, 83, 8, 96, 24, 64);
            pixmap.setColor(baseColor);
            pixmap.fillRectangle(28, 49, 61, 30);
            pixmap.setColor(light);
            pixmap.fillRectangle(32, 52, 49, 8);
            pixmap.setColor(Color.WHITE);
            pixmap.fillCircle(58, 64, 9);
            pixmap.setColor(1f, 0.82f, 0.25f, 1f);
            pixmap.fillTriangle(8, 64, 0, 48, 0, 80);
        } else {
            pixmap.setColor(dark);
            pixmap.fillRectangle(45, 36, 38, 68);
            pixmap.fillTriangle(38, 36, 64, 10, 90, 36);
            pixmap.fillTriangle(45, 104, 32, 120, 64, 104);
            pixmap.fillTriangle(83, 104, 96, 120, 64, 104);
            pixmap.setColor(baseColor);
            pixmap.fillRectangle(49, 39, 30, 61);
            pixmap.setColor(light);
            pixmap.fillRectangle(52, 46, 8, 43);
            pixmap.setColor(Color.WHITE);
            pixmap.fillCircle(64, 70, 9);
            pixmap.setColor(1f, 0.82f, 0.25f, 1f);
            pixmap.fillTriangle(64, 120, 48, 128, 80, 128);
        }

        pixmap.setColor(1f, 1f, 1f, 0.55f);
        pixmap.drawCircle(64, 64, 54);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture createBombTexture(Color baseColor) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        Color dark = new Color(baseColor).lerp(Color.BLACK, 0.45f);
        Color light = new Color(baseColor).lerp(Color.WHITE, 0.55f);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        pixmap.setColor(0f, 0f, 0f, 0.32f);
        pixmap.fillCircle(68, 72, 51);
        pixmap.setColor(dark);
        pixmap.fillCircle(64, 68, 42);
        pixmap.setColor(baseColor);
        pixmap.fillCircle(60, 64, 39);
        pixmap.setColor(light);
        pixmap.fillCircle(47, 48, 15);

        pixmap.setColor(0.18f, 0.16f, 0.12f, 1f);
        pixmap.fillRectangle(78, 23, 10, 25);
        pixmap.fillTriangle(75, 26, 93, 26, 84, 13);
        pixmap.setColor(1f, 0.78f, 0.22f, 1f);
        pixmap.fillCircle(84, 13, 8);
        pixmap.setColor(1f, 0.28f, 0.14f, 1f);
        pixmap.fillCircle(84, 13, 4);

        pixmap.setColor(1f, 1f, 1f, 0.58f);
        pixmap.drawCircle(60, 64, 39);
        pixmap.drawCircle(60, 64, 40);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private Texture createLightningTexture(Color baseColor) {
        int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.SourceOver);

        Color glow = new Color(baseColor).lerp(Color.WHITE, 0.55f);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(0f, 0f, 0f, 0.30f);
        pixmap.fillCircle(68, 72, 51);
        pixmap.setColor(baseColor);
        pixmap.fillCircle(64, 64, 46);
        pixmap.setColor(1f, 1f, 1f, 0.20f);
        pixmap.fillCircle(64, 64, 39);

        pixmap.setColor(1f, 0.94f, 0.26f, 1f);
        pixmap.fillTriangle(71, 8, 38, 70, 63, 67);
        pixmap.fillTriangle(63, 67, 49, 120, 93, 52);
        pixmap.setColor(glow);
        pixmap.fillTriangle(67, 18, 47, 61, 64, 59);
        pixmap.fillTriangle(64, 59, 56, 102, 82, 58);
        pixmap.setColor(1f, 1f, 1f, 0.70f);
        pixmap.drawCircle(64, 64, 50);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private void drawDiamondGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillTriangle(64, 8, 120, 44, 64, 122);
        pixmap.fillTriangle(64, 8, 64, 122, 8, 44);
        pixmap.setColor(baseColor);
        pixmap.fillTriangle(8, 44, 64, 8, 42, 44);
        pixmap.fillTriangle(42, 44, 64, 122, 8, 44);
        pixmap.fillTriangle(64, 8, 86, 44, 120, 44);
        pixmap.fillTriangle(86, 44, 120, 44, 64, 122);
        pixmap.setColor(light);
        pixmap.fillTriangle(42, 44, 64, 8, 86, 44);
        pixmap.fillTriangle(42, 44, 86, 44, 64, 122);
        drawGemLines(pixmap, 64, 8, 120, 44, 64, 122, 8, 44);
    }

    private void drawRoundGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillCircle(66, 66, 55);
        pixmap.setColor(baseColor);
        pixmap.fillCircle(62, 62, 50);
        pixmap.setColor(light);
        pixmap.fillCircle(49, 45, 24);
        pixmap.setColor(1f, 1f, 1f, 0.42f);
        pixmap.drawCircle(62, 62, 50);
        pixmap.drawLine(27, 64, 97, 64);
        pixmap.drawLine(62, 14, 62, 112);
    }

    private void drawSquareGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillRectangle(27, 27, 78, 78);
        pixmap.setColor(baseColor);
        pixmap.fillRectangle(22, 22, 78, 78);
        pixmap.setColor(light);
        pixmap.fillTriangle(22, 22, 100, 22, 22, 100);
        pixmap.setColor(1f, 1f, 1f, 0.48f);
        pixmap.drawRectangle(22, 22, 78, 78);
        pixmap.drawLine(22, 22, 100, 100);
        pixmap.drawLine(100, 22, 22, 100);
    }

    private void drawHexGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillRectangle(29, 28, 70, 76);
        pixmap.fillTriangle(29, 28, 64, 8, 99, 28);
        pixmap.fillTriangle(29, 104, 99, 104, 64, 122);
        pixmap.setColor(baseColor);
        pixmap.fillRectangle(25, 30, 70, 68);
        pixmap.fillTriangle(25, 30, 64, 12, 95, 30);
        pixmap.fillTriangle(25, 98, 95, 98, 64, 116);
        pixmap.setColor(light);
        pixmap.fillTriangle(25, 30, 64, 12, 64, 64);
        pixmap.fillTriangle(25, 30, 64, 64, 25, 98);
        drawGemLines(pixmap, 64, 12, 95, 30, 64, 116, 25, 98);
    }

    private void drawTriangleGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillTriangle(64, 10, 118, 112, 10, 112);
        pixmap.setColor(baseColor);
        pixmap.fillTriangle(64, 6, 112, 108, 16, 108);
        pixmap.setColor(light);
        pixmap.fillTriangle(64, 6, 64, 108, 16, 108);
        pixmap.setColor(1f, 1f, 1f, 0.48f);
        pixmap.drawLine(64, 6, 112, 108);
        pixmap.drawLine(112, 108, 16, 108);
        pixmap.drawLine(16, 108, 64, 6);
        pixmap.drawLine(64, 6, 64, 108);
    }

    private void drawShardGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
        pixmap.setColor(dark);
        pixmap.fillTriangle(62, 6, 104, 42, 74, 122);
        pixmap.fillTriangle(62, 6, 74, 122, 18, 78);
        pixmap.setColor(baseColor);
        pixmap.fillTriangle(62, 6, 96, 44, 60, 70);
        pixmap.fillTriangle(60, 70, 74, 122, 18, 78);
        pixmap.fillTriangle(60, 70, 96, 44, 74, 122);
        pixmap.setColor(light);
        pixmap.fillTriangle(62, 6, 60, 70, 18, 78);
        pixmap.setColor(1f, 1f, 1f, 0.48f);
        pixmap.drawLine(62, 6, 104, 42);
        pixmap.drawLine(104, 42, 74, 122);
        pixmap.drawLine(74, 122, 18, 78);
        pixmap.drawLine(18, 78, 62, 6);
        pixmap.drawLine(60, 70, 74, 122);
    }

    private void drawGemLines(Pixmap pixmap, int topX, int topY, int rightX, int rightY, int bottomX, int bottomY, int leftX, int leftY) {
        pixmap.setColor(1f, 1f, 1f, 0.5f);
        pixmap.drawLine(topX, topY, rightX, rightY);
        pixmap.drawLine(rightX, rightY, bottomX, bottomY);
        pixmap.drawLine(bottomX, bottomY, leftX, leftY);
        pixmap.drawLine(leftX, leftY, topX, topY);
        pixmap.drawLine(leftX, leftY, rightX, rightY);
        pixmap.drawLine(topX, topY, bottomX, bottomY);
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

    private boolean sameGemType(int first, int second) {
        return first != EMPTY && second != EMPTY && baseGemType(first) == baseGemType(second);
    }

    private int baseGemType(int cell) {
        if (isHorizontalRocket(cell)) {
            return cell - ROCKET_HORIZONTAL;
        }
        if (isVerticalRocket(cell)) {
            return cell - ROCKET_VERTICAL;
        }
        if (isBomb(cell)) {
            return cell - BOMB;
        }
        if (isLightning(cell)) {
            return cell - LIGHTNING;
        }
        return cell;
    }

    private int makeRocket(int gemType, boolean horizontal) {
        return (horizontal ? ROCKET_HORIZONTAL : ROCKET_VERTICAL) + gemType;
    }

    private boolean isRocket(int cell) {
        return isHorizontalRocket(cell) || isVerticalRocket(cell);
    }

    private boolean isBooster(int cell) {
        return isRocket(cell) || isBomb(cell) || isLightning(cell);
    }

    private int makeBomb(int gemType) {
        return BOMB + gemType;
    }

    private int makeLightning(int gemType) {
        return LIGHTNING + gemType;
    }

    private boolean isHorizontalRocket(int cell) {
        return cell >= ROCKET_HORIZONTAL && cell < ROCKET_HORIZONTAL + GEM_TYPES;
    }

    private boolean isVerticalRocket(int cell) {
        return cell >= ROCKET_VERTICAL && cell < ROCKET_VERTICAL + GEM_TYPES;
    }

    private boolean isBomb(int cell) {
        return cell >= BOMB && cell < BOMB + GEM_TYPES;
    }

    private boolean isLightning(int cell) {
        return cell >= LIGHTNING && cell < LIGHTNING + GEM_TYPES;
    }

    private float topHudHeight() {
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.22f, 152f, 230f);
    }

    private float bottomHudHeight() {
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.14f, 112f, 150f);
    }

    private float cellSize() {
        float availableWidth = Gdx.graphics.getWidth() * 0.92f;
        float availableHeight = Gdx.graphics.getHeight() - topHudHeight() - bottomHudHeight();
        return Math.max(38f, Math.min(availableWidth, availableHeight) / BOARD_SIZE);
    }

    private float boardX() {
        return (Gdx.graphics.getWidth() - cellSize() * BOARD_SIZE) * 0.5f;
    }

    private float boardY() {
        float playBottom = bottomHudHeight();
        float playHeight = Gdx.graphics.getHeight() - topHudHeight() - bottomHudHeight();
        return playBottom + (playHeight - cellSize() * BOARD_SIZE) * 0.48f;
    }

    private float restartButtonWidth() {
        return restartButtonSize();
    }

    private float restartButtonHeight() {
        return restartButtonSize();
    }

    private float restartButtonSize() {
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.082f, 72f, 94f);
    }

    private float restartButtonX() {
        return (Gdx.graphics.getWidth() - restartButtonWidth()) * 0.5f;
    }

    private float restartButtonY() {
        return Math.max(22f, bottomHudHeight() * 0.18f);
    }

    private boolean isRestartButtonHit(int screenX, int worldY) {
        return screenX >= restartButtonX()
            && screenX <= restartButtonX() + restartButtonWidth()
            && worldY >= restartButtonY()
            && worldY <= restartButtonY() + restartButtonHeight();
    }

    private boolean isRestartButtonHovering() {
        return isRestartButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private float hudMenuButtonWidth() {
        return MathUtils.clamp(Gdx.graphics.getWidth() * 0.18f, 104f, 138f);
    }

    private float hudMenuButtonHeight() {
        return restartButtonHeight();
    }

    private float hudMenuButtonX() {
        return Math.max(18f, restartButtonX() - hudMenuButtonWidth() - MENU_BUTTON_GAP);
    }

    private float hudMenuButtonY() {
        return restartButtonY();
    }

    private boolean isMenuButtonHit(int screenX, int worldY) {
        return screenX >= hudMenuButtonX()
            && screenX <= hudMenuButtonX() + hudMenuButtonWidth()
            && worldY >= hudMenuButtonY()
            && worldY <= hudMenuButtonY() + hudMenuButtonHeight();
    }

    private boolean isHudMenuButtonHovering() {
        return isMenuButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private float menuButtonWidth() {
        return MathUtils.clamp(menuPanelWidth() * 0.62f, 230f, 300f);
    }

    private float menuActionButtonHeight() {
        return MathUtils.clamp(menuPanelHeight() * 0.16f, 54f, 66f);
    }

    private float menuPanelWidth() {
        return MathUtils.clamp(Gdx.graphics.getWidth() * 0.74f, 360f, 500f);
    }

    private float menuPanelHeight() {
        return MathUtils.clamp(Gdx.graphics.getHeight() * 0.42f, 330f, 395f);
    }

    private float menuPanelX() {
        return (Gdx.graphics.getWidth() - menuPanelWidth()) * 0.5f;
    }

    private float menuPanelY() {
        return (Gdx.graphics.getHeight() - menuPanelHeight()) * 0.5f;
    }

    private float playButtonX() {
        return (Gdx.graphics.getWidth() - menuButtonWidth()) * 0.5f;
    }

    private float playButtonY() {
        float groupHeight = menuActionButtonHeight() * 2f + menuButtonGap();
        return menuPanelY() + menuPanelHeight() * 0.24f + groupHeight - menuActionButtonHeight();
    }

    private float exitButtonX() {
        return playButtonX();
    }

    private float exitButtonY() {
        return playButtonY() - menuActionButtonHeight() - menuButtonGap();
    }

    private float menuButtonGap() {
        return MathUtils.clamp(menuPanelHeight() * 0.06f, 18f, 24f);
    }

    private boolean isPlayButtonHit(int screenX, int worldY) {
        return screenX >= playButtonX()
            && screenX <= playButtonX() + menuButtonWidth()
            && worldY >= playButtonY()
            && worldY <= playButtonY() + menuActionButtonHeight();
    }

    private boolean isPlayButtonHovering() {
        return isPlayButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private boolean isExitButtonHit(int screenX, int worldY) {
        return screenX >= exitButtonX()
            && screenX <= exitButtonX() + menuButtonWidth()
            && worldY >= exitButtonY()
            && worldY <= exitButtonY() + menuActionButtonHeight();
    }

    private boolean isExitButtonHovering() {
        return isExitButtonHit(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
    }

    private float hudScale(float multiplier) {
        return multiplier * MathUtils.clamp(Gdx.graphics.getWidth() / 720f, 0.72f, 1.05f);
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

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        background.dispose();
        restartIcon.dispose();
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
