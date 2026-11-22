# CS2 Coinflip & Map Veto

Версия 1.1.0 использует [Midnight Amber](MIDNIGHT-AMBER-DESIGN.md), новый логотип и две текстуры монетки. Монетка вращается вокруг горизонтальной оси и останавливается на выпавшей стороне.

В версии 1.1.1 в шапке появилась подпись `by gearbarium`. В лаунчере Android приложение называется CS2 Coinflip. На обеих платформах иконка использует знак из логотипа.

В версии 1.1.2 решающая карта вынесена в отдельную карточку. Обе платформы используют присланную иконку с надписью CS2. В Windows скруглены кнопки и улучшена фильтрация текстур при уменьшении.

Два офлайн-приложения для турнирной жеребьёвки. Windows использует Rust и egui/eframe. Android использует Kotlin, Jetpack Compose, Material 3, ViewModel, StateFlow и DataStore.

## Матч

Введите команды, выберите BO и подбросьте монетку. Победитель определяет первый ход. В настройках это решение можно передать приложению, тогда победитель автоматически ходит первым. Сторона монетки выбирается системным криптографическим генератором до начала анимации.

Нажмите доступную карту, чтобы выполнить показанный BAN или PICK. Опция подтверждения включена по умолчанию. После шести ходов оставшаяся карта становится DECIDER. Отмена последнего хода возвращает карту в пул и снимает статус DECIDER. Сброс требует подтверждения.

У завершённого матча есть история, копирование результата и экспорт PNG. Windows сохраняет PNG в `%LOCALAPPDATA%\CS2 Coinflip\Результаты` и показывает его в проводнике. Android открывает системный выбор места сохранения.

Состояние Windows хранится в `%LOCALAPPDATA%\CS2 Coinflip\match.json`, состояние Android — в хранилище DataStore приложения. При восстановлении незавершённого матча приложение предлагает продолжить с сохранённым результатом подбрасывания. Пул и формат фиксируются с момента жеребьёвки до сброса. Пул должен содержать семь уникальных непустых карт.

## Windows

Установите Rust и компоновщик целевой платформы. Для MSVC запустите команды из Developer PowerShell с Windows SDK и `rc.exe`. Для GNU нужен совместимый MinGW-w64 с `windres` в PATH. При необходимости переменная `WINDRES` задаёт путь к компилятору ресурсов.

```powershell
Set-Location windows
cargo fmt --check
cargo test --locked
cargo clippy --locked --all-targets -- -D warnings
cargo build --release --locked
```

Результат находится в `windows/target/release/cs2-coinflip.exe`. Шрифт и правила встроены.

## Android

Требуются JDK 17, Android SDK 35 и Platform Tools. Укажите путь к SDK через `ANDROID_HOME` или `android/local.properties`. Gradle Wrapper использует Gradle 8.9.

```powershell
Set-Location android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
adb devices -l
adb -s <серийный-номер> install -r app/build/outputs/apk/debug/app-debug.apk
```

Приложение поддерживает Android 8.0 и новее. Для установки через ADB включите отладку по USB и разрешите подключение компьютера на телефоне.

`:app:assembleRelease` без ключа создаёт неподписанный APK. Для подписанной сборки задайте значения в окружении перед запуском Gradle:

- `COINFLIP_KEYSTORE` — путь к keystore владельца.
- `COINFLIP_STORE_PASSWORD` — пароль хранилища.
- `COINFLIP_KEY_ALIAS` — алиас ключа.
- `COINFLIP_KEY_PASSWORD` — пароль ключа.

Проверьте подписанную сборку через `apksigner verify --verbose <apk>`. Тестовый APK подписан отладочным ключом. Чтобы заменить его пакетом с подписью владельца, Android потребует удалить отладочную установку, что также удалит её локальные данные.

## Общий контракт

Обе реализации читают [veto-rules.json](shared-spec/veto-rules.json) и используют одинаковые примеры из [test-cases.json](shared-spec/test-cases.json). Чётные шаги (при нумерации с нуля) выполняет Team A. Нечётные выполняет Team B. Пики сохраняют порядок ходов. DECIDER идёт последним. Это правила приложения по умолчанию.

[state-schema.json](shared-spec/state-schema.json) описывает JSON версии 1. Поля `winner` и `first` хранят индексы 0 или 1, а `moves` — индексы карт в активном пуле. Приложения вычисляют участников ходов, статусы, номера игровых карт и DECIDER из этого состояния.

Inter распространяется по SIL Open Font License 1.1. Текст лицензии находится в ресурсах обеих сборок.

## Проверка сборок

- Windows x64: `cargo fmt --check`, `cargo test --locked` (2 теста), `cargo clippy --locked --all-targets -- -D warnings`, `cargo build --release --locked`. Проверен запуск окна и переход в развёрнутый режим.
- Android: `:app:testDebugUnitTest` (3 теста), `:app:assembleDebug`, `:app:assembleRelease` и `:app:assembleDebugAndroidTest`. На эмуляторе прошёл Compose-тест подтверждения выбора карты и отмены хода. Проверены портретная и альбомная компоновки.
- Отладочный APK проверен через `apksigner`, установлен через ADB и запущен на HONOR ELP_NX9. Release APK собран без производственной подписи.
