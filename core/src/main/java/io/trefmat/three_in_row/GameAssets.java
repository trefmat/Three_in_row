package io.trefmat.three_in_row;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

final class GameAssets {
    private GameAssets() {
    }

    static Texture createBackgroundTexture() {
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

    static Texture loadGemTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    static Texture loadBombTexture(String colorName, Color fallbackColor) {
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

    static Texture loadRocketTexture(String colorName, Color fallbackColor, boolean horizontal) {
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

    static Texture loadRocketTexture(String colorName, Color fallbackColor) {
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

    static Texture loadLightningTexture(String colorName, Color fallbackColor) {
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

    static Texture loadTextureMatching(String[] directories, String[] requiredTokens, String[] optionalTokens) {
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

    static boolean containsAllTokens(String name, String[] tokens) {
        for (String token : tokens) {
            if (!name.contains(token)) {
                return false;
            }
        }
        return true;
    }

    static boolean containsAnyToken(String name, String[] tokens) {
        for (String token : tokens) {
            if (name.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static Texture createGemTexture(Color baseColor, int gemType) {
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

    static Texture createRocketTexture(Color baseColor, boolean horizontal) {
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

    static Texture createBombTexture(Color baseColor) {
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

    static Texture createLightningTexture(Color baseColor) {
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

    static void drawDiamondGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawRoundGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawSquareGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawHexGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawTriangleGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawShardGem(Pixmap pixmap, Color baseColor, Color dark, Color light) {
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

    static void drawGemLines(Pixmap pixmap, int topX, int topY, int rightX, int rightY, int bottomX, int bottomY, int leftX, int leftY) {
        pixmap.setColor(1f, 1f, 1f, 0.5f);
        pixmap.drawLine(topX, topY, rightX, rightY);
        pixmap.drawLine(rightX, rightY, bottomX, bottomY);
        pixmap.drawLine(bottomX, bottomY, leftX, leftY);
        pixmap.drawLine(leftX, leftY, topX, topY);
        pixmap.drawLine(leftX, leftY, rightX, rightY);
        pixmap.drawLine(topX, topY, bottomX, bottomY);
    }

}
