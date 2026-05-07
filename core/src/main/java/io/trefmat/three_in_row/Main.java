package io.trefmat.three_in_row;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
    private static final float INVALID_FLASH_TIME = 0.25f;
    private static final float SWAP_TIME = 0.18f;
    private static final float FALL_TIME = 0.28f;
    private static final float INVALID_SWAP_TIME = 0.13f;
    private static final float MATCH_CLEAR_TIME = 0.22f;
    private static final float BOOSTER_BLAST_TIME = 0.36f;

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

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private GlyphLayout glyphLayout;
    private Texture background;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private int score;
    private int moves;
    private float invalidFlash;
    private String statusText = "Pick a crystal";
    private AnimationState animationState = AnimationState.IDLE;
    private float animationTimer;
    private float animationDuration;
    private int swapRowA;
    private int swapColA;
    private int swapRowB;
    private int swapColB;
    private int boosterPreferredRow = -1;
    private int boosterPreferredCol = -1;
    private int boosterFallbackRow = -1;
    private int boosterFallbackCol = -1;
    private int pendingRemoved;

    private enum AnimationState {
        IDLE,
        VALID_SWAP,
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
        font.getData().setScale(1.35f);
        glyphLayout = new GlyphLayout();
        background = createBackgroundTexture();

        Color[] colors = {
            Color.valueOf("29D6FF"),
            Color.valueOf("4CF07A"),
            Color.valueOf("FF4F6D"),
            Color.valueOf("FFD84A"),
            Color.valueOf("A967FF"),
            Color.valueOf("FF8B3D")
        };
        for (int i = 0; i < GEM_TYPES; i++) {
            gemTextures[i] = createGemTexture(colors[i], i);
            rocketHorizontalTextures[i] = createRocketTexture(colors[i], true);
            rocketVerticalTextures[i] = createRocketTexture(colors[i], false);
            bombTextures[i] = createBombTexture(colors[i]);
        }

        resetBoard();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT || button == -1) {
                    handleBoardTap(screenX, screenY);
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.R) {
                    resetBoard();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        invalidFlash = Math.max(0f, invalidFlash - delta);
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
        if (animationState != AnimationState.IDLE) {
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

    private void beginSwap(int rowA, int colA, int rowB, int colB) {
        swapRowA = rowA;
        swapColA = colA;
        swapRowB = rowB;
        swapColB = colB;

        swap(rowA, colA, rowB, colB);

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
                    if (board[row][runStart] != EMPTY && runLength >= 4) {
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
                    if (board[runStart][col] != EMPTY && runLength >= 4) {
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
                if (isRocket(board[row][col])) {
                    if (isHorizontalRocket(board[row][col])) {
                        blastHorizontalRockets[row][col] = true;
                    } else {
                        blastVerticalRockets[row][col] = true;
                    }
                    markRocketBlast(cellsToClear, row, col);
                    activatedBooster = true;
                } else if (isBomb(board[row][col])) {
                    blastBombs[row][col] = true;
                    markBombBlast(cellsToClear, row, col);
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
                float size = cell - localPadding * 2f;
                if (clearing) {
                    batch.setColor(1f, 1f, 1f, 1f - clearProgress * 0.55f);
                } else {
                    batch.setColor(Color.WHITE);
                }
                batch.draw(
                    textureForCell(gem),
                    boardX() + drawCols[row][col] * cell + localPadding,
                    boardY() + drawRows[row][col] * cell + localPadding,
                    size,
                    size
                );
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
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
        String title = "CRYSTAL MATCH";
        String stats = "Score: " + score + "   Moves: " + moves;
        String hint = statusText + "     R - reset";

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.03f, 0.04f, 0.065f, 0.72f);
        shapes.rect(0f, Gdx.graphics.getHeight() - 76f, Gdx.graphics.getWidth(), 76f);
        shapes.setColor(0.03f, 0.04f, 0.065f, 0.65f);
        shapes.rect(0f, 0f, Gdx.graphics.getWidth(), 64f);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        font.setColor(Color.valueOf("F8FBFF"));
        glyphLayout.setText(font, title);
        font.draw(batch, glyphLayout, (Gdx.graphics.getWidth() - glyphLayout.width) * 0.5f, Gdx.graphics.getHeight() - 20f);

        font.setColor(Color.valueOf("B7C8FF"));
        glyphLayout.setText(font, stats);
        font.draw(batch, glyphLayout, (Gdx.graphics.getWidth() - glyphLayout.width) * 0.5f, Gdx.graphics.getHeight() - 52f);

        font.setColor(invalidFlash > 0f ? Color.valueOf("FFB6BA") : Color.valueOf("B6BED4"));
        glyphLayout.setText(font, hint);
        font.draw(batch, glyphLayout, (Gdx.graphics.getWidth() - glyphLayout.width) * 0.5f, 39f);
        batch.end();
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
        return cell;
    }

    private int makeRocket(int gemType, boolean horizontal) {
        return (horizontal ? ROCKET_HORIZONTAL : ROCKET_VERTICAL) + gemType;
    }

    private boolean isRocket(int cell) {
        return isHorizontalRocket(cell) || isVerticalRocket(cell);
    }

    private boolean isBooster(int cell) {
        return isRocket(cell) || isBomb(cell);
    }

    private int makeBomb(int gemType) {
        return BOMB + gemType;
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

    private float cellSize() {
        float availableWidth = Gdx.graphics.getWidth() * 0.9f;
        float availableHeight = Gdx.graphics.getHeight() - 150f;
        return Math.max(38f, Math.min(availableWidth, availableHeight) / BOARD_SIZE);
    }

    private float boardX() {
        return (Gdx.graphics.getWidth() - cellSize() * BOARD_SIZE) * 0.5f;
    }

    private float boardY() {
        return (Gdx.graphics.getHeight() - cellSize() * BOARD_SIZE) * 0.5f + 5f;
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
        for (Texture texture : gemTextures) {
            texture.dispose();
        }
        for (Texture texture : rocketHorizontalTextures) {
            texture.dispose();
        }
        for (Texture texture : rocketVerticalTextures) {
            texture.dispose();
        }
        for (Texture texture : bombTextures) {
            texture.dispose();
        }
    }
}
