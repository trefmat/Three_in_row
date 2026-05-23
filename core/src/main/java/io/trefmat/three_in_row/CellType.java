package io.trefmat.three_in_row;

final class CellType {
    static final int EMPTY = -1;
    private static final int ROCKET_HORIZONTAL = 100;
    private static final int ROCKET_VERTICAL = 200;
    private static final int BOMB = 300;
    private static final int LIGHTNING = 400;

    private CellType() {
    }

    static boolean sameGemType(int first, int second) {
        return first != EMPTY && second != EMPTY && baseGemType(first) == baseGemType(second);
    }

    static int baseGemType(int cell) {
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

    static int makeRocket(int gemType, boolean horizontal) {
        return (horizontal ? ROCKET_HORIZONTAL : ROCKET_VERTICAL) + gemType;
    }

    static boolean isRocket(int cell) {
        return isHorizontalRocket(cell) || isVerticalRocket(cell);
    }

    static boolean isBooster(int cell) {
        return isRocket(cell) || isBomb(cell) || isLightning(cell);
    }

    static int makeBomb(int gemType) {
        return BOMB + gemType;
    }

    static int makeLightning(int gemType) {
        return LIGHTNING + gemType;
    }

    static boolean isHorizontalRocket(int cell) {
        return cell >= ROCKET_HORIZONTAL && cell < ROCKET_HORIZONTAL + Main.GEM_TYPES;
    }

    static boolean isVerticalRocket(int cell) {
        return cell >= ROCKET_VERTICAL && cell < ROCKET_VERTICAL + Main.GEM_TYPES;
    }

    static boolean isBomb(int cell) {
        return cell >= BOMB && cell < BOMB + Main.GEM_TYPES;
    }

    static boolean isLightning(int cell) {
        return cell >= LIGHTNING && cell < LIGHTNING + Main.GEM_TYPES;
    }
}
