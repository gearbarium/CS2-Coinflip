# CS2 Coinflip & Map Veto

Офлайн-приложение для жеребьёвки и выбора карт в Counter-Strike 2. Поддерживает BO1, BO3 и BO5, возобновление матча и экспорт результата в PNG.

## Скачать

[Последний релиз](https://github.com/gearbarium/CS2-Coinflip/releases/latest)

- **Windows x64.** Запустите EXE или распакуйте portable ZIP.
- **Android 8.0 и новее.** Установите APK.

## Как играть

Введите названия команд, выберите формат и подбросьте монетку. Победитель выбирает, кто ходит первым. Команды по очереди банят и выбирают карты. Последняя карта становится решающей.

## Сборка из исходников

- **Windows.** В папке `windows` выполните `cargo build --release --locked`. Нужен Rust с Windows SDK (`rc.exe`) или MinGW-w64 (`windres`).
- **Android.** В папке `android` выполните `.\gradlew.bat :app:assembleDebug`. Нужны JDK 17 и Android SDK 35.
