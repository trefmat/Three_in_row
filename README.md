# Three in Row

`Three in Row` — казуальная игра в жанре match-3, написанная на Java с использованием [libGDX](https://libgdx.com/). Игрок меняет соседние кристаллы местами, собирает линии из трех и более одинаковых элементов, создает бустеры и набирает очки.

## Возможности

- Поле 8x8 с классической механикой "три в ряд".
- Обычные кристаллы шести цветов.
- Бустеры:
  - ракеты для очистки строки или столбца;
  - бомбы для взрыва области;
  - молнии для очистки кристаллов выбранного цвета.
- Комбинации бустеров.
- Анимации обмена, падения, очистки и перезапуска.
- Адаптивный масштаб интерфейса для desktop и Android.
- Android APK с launcher-иконкой.

## Структура проекта

- `core` — общая игровая логика и рендеринг.
- `lwjgl3` — desktop-запуск через LWJGL3.
- `android` — Android-приложение и ресурсы APK.
- `assets` — игровые изображения: кристаллы, ракеты, бомбы, иконки.

Основные классы в `core`:

- `Main` — основной цикл игры, ввод, состояние партии и рендеринг.
- `GameLayout` — размеры, позиции элементов UI и адаптивный масштаб.
- `GameAssets` — загрузка и генерация текстур.
- `CellType` — типы клеток: обычные кристаллы, ракеты, бомбы, молнии.
- `GameConfig` — общие настройки поля.
- `GameScreen` и `AnimationState` — состояния экрана и анимаций.

## Запуск на компьютере

Windows:

```bat
.\gradlew.bat lwjgl3:run
```

Linux/macOS:

```bash
./gradlew lwjgl3:run
```

## Сборка Android APK

Для сборки нужен установленный Android SDK.

```bat
.\gradlew.bat :android:assembleDebug
```

Готовый debug APK будет находиться в:

```text
android/build/outputs/apk/debug/
```

## Полезные команды Gradle

```bat
.\gradlew.bat core:compileJava
```

Проверить компиляцию общего Java-кода.

```bat
.\gradlew.bat :android:assembleDebug
```

Собрать Android APK.

```bat
.\gradlew.bat lwjgl3:run
```

Запустить desktop-версию.

```bat
.\gradlew.bat clean
```

Очистить build-директории.

## Ассеты

Игровые ассеты лежат в папке `assets`:

- `assets/gems` — кристаллы;
- `assets/rockets` — ракеты;
- `assets/bombs` — бомбы;
- `assets/restart.png` — иконка кнопки перезапуска.
