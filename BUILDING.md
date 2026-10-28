# Сборка OldYM через Gradle

[English](BUILDING.en.md)

## Что изменяется

Сборка использует Gradle Wrapper 7.6.4 и отдельные задачи Gradle для ресурсов,
Java, JAR, переноса Bouncy Castle, ProGuard, DEX, упаковки и подписи.
Это **не Android Gradle Plugin (AGP)** и не оболочка над `build_portable.py`.
Исходники остаются в `src/`, ресурсы в `res/`, приложение сохраняет ID `ru.oldym`.
Переезд в `app/src/main` не требуется. Импорт как стандартного Android-модуля
в Android Studio этой конфигурацией не предоставляется.

Используется прежняя цепочка API 5: `aapt`, Java 7 bytecode, ProGuard и `dx`.
Это сохраняет формат DEX 035. Сборка не добавляет новые функции приложения.

## Требования

- Полный существующий проект с `AndroidManifest.xml`, `src/`, `res/`, `assets/`,
  `proguard.cfg` и `tools/`.
- **JDK 11**, не JRE. `JAVA_HOME` указывает на каталог JDK без `bin` в конце.
- **Python 3.8+** для `tools/relocate.py`. Linux: `python3`; Windows: `py -3`.
  Для другого исполняемого файла используй `-Ppython=python` или полный путь.
- Интернет при первом запуске: Wrapper скачает Gradle 7.6.4 и проверит SHA-256.
  Сам Gradle отдельно устанавливать не нужно.
- Linux **x86-64**: используется имеющийся `tools/sdk/android-9/aapt`.
  Должны быть доступны его системные динамические библиотеки.
- Windows **x64**: установи **Android SDK Build Tools 28.0.3 для Windows**
  через SDK Manager. Не копируй только EXE: оставь рядом DLL из комплекта.
  Linux-версия `aapt` на Windows не работает.

В `tools/` должны остаться `api5/android-2.0_r01-linux/android.jar`,
`sdk/android-9/lib/dx.jar`, `sdk/android-9/lib/apksigner.jar`,
`proguard-base.jar`, `bcprov.jar`, `bctls.jar`, `bcutil.jar`, `relocate.py`.
Обновление сборки не содержит повторно эти зависимости и исходники приложения.

## Установка обновления

Распакуй `OldYM-Gradle-build-update.zip` **в корень своего актуального проекта**
с заменой сборочных файлов. Внутри архива нет исходников приложения, ресурсов,
ключей или готовых APK. Не заменяй свою актуальную версию старым полным проектом.

`src/`, `res/`, `assets/` и исходный `AndroidManifest.xml` обновление не меняет.
При сборке создаётся отдельная копия манифеста: относительные имена компонентов
сверяются с классами исходников. Это учитывает существующее различие между
ID `ru.oldym` и Java-пакетом `ru.retromusic`, не меняя ID приложения.

## Linux

Команды выполняются из корня проекта:

```sh
export JAVA_HOME=/path/to/jdk-11
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
python3 --version
chmod +x gradlew build.sh
./gradlew assembleDebug
```

Вместо пути подставь каталог установленной JDK 11.
Если executable bit потерялся при загрузке ZIP, можно выполнить `sh gradlew assembleDebug`.
Эквивалентная короткая команда: `sh build.sh`.

## Windows

Пример для **cmd**, не PowerShell:

```bat
set "JAVA_HOME=C:\Java\jdk-11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_SDK_ROOT=C:\Android\Sdk"
java -version
javac -version
py -3 --version
gradlew.bat assembleDebug
```

Если `aapt.exe` лежит в другом месте:

```bat
gradlew.bat assembleDebug "-Paapt=C:/Android/Sdk/build-tools/28.0.3/aapt.exe"
```

Поиск Windows `aapt.exe`: параметр `-Paapt`, переменная `AAPT`,
`tools/windows/aapt.exe`, SDK из `ANDROID_SDK_ROOT` / `ANDROID_HOME`,
затем `%LOCALAPPDATA%/Android/Sdk/build-tools/28.0.3/aapt.exe`.
Эквивалентная короткая команда: `build.cmd`.
Для PowerShell запускай `./gradlew.bat`, а переменные задавай через `$env:...`.

## Результаты и задачи

| Задача | Результат |
| --- | --- |
| `assembleDebug` | `build/outputs/apk/debug/OldYM-debug.apk`, локальная debug-подпись и её проверка |
| `assembleUnsigned` | `build/unsigned.apk`, без подписи, для установки не подходит |
| `assembleRelease` без ключа | `build/outputs/apk/release/OldYM-release-unsigned.apk` |
| `assembleRelease` с ключом | дополнительно `build/outputs/apk/release/OldYM-release.apk`, подпись проверяется |
| `checkBuildEnvironment` | проверка JDK и обязательных инструментов |
| `clean` | удаление `build/` |
| `tasks --all` | список этапов сборки |

Windows: перед задачей `gradlew.bat`; Linux: `./gradlew`.
`build.sh` и `build.cmd` теперь передают аргументы Gradle, а не Python.
Старые параметры вида `build.cmd --unsigned` заменены на `build.cmd assembleUnsigned`.

При повторной сборке Gradle пропускает неизменившиеся этапы.
Debug-ключ хранится в `GRADLE_USER_HOME/oldym/debug.keystore` (обычно
`~/.gradle/oldym/debug.keystore`), а не в `build/`: `clean` его не удаляет.
Это ключ только локальных тестов; для публичных выпусков используй свой постоянный.
Собранный APK сохраняет настройку `android:debuggable` из исходного манифеста:
слово Debug здесь обозначает локальную подпись, а не автоматическое включение отладки.

## Создание ключа официальных выпусков

**Если ключ уже есть, новый не создавай.** Обновления должны иметь прежнюю подпись.
Закрытый ключ хранится вне репозитория; сделай приватную резервную копию и сохрани пароль.
Для Linux:

```sh
mkdir -p "$HOME/.oldym"
"$JAVA_HOME/bin/keytool" -genkeypair -storetype JKS -keystore "$HOME/.oldym/oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

Для Windows cmd (сначала создай папку `D:\Keys` или укажи свою):

```bat
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -storetype JKS -keystore "D:\Keys\oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

`keytool` запросит пароль и сведения владельца интерактивно.
Alias в примере — `oldym`; для существующего ключа используй его реальный alias.
Просмотр alias:

```sh
keytool -list -keystore /path/to/oldym-release.jks
```

## Подпись релиза

Скопируй `signing.properties.example` в `signing.properties` в корне проекта.
Укажи локально путь к **своему** ключу, alias и пароли:

```properties
oldym.keystore=D:/Keys/oldym-release.jks
oldym.alias=oldym
oldym.storePassword=YOUR_LOCAL_PASSWORD
oldym.keyPassword=YOUR_LOCAL_PASSWORD
```

Linux: вместо `D:/...` используй `/home/you/.oldym/oldym-release.jks`.
Windows: прямые слеши `/` в properties не требуют экранирования.
Файл использует синтаксис Java properties: обратные слеши в значениях нужно
экранировать как `\\`, не заключай пароль в кавычки без необходимости.
Для паролей со сложными символами предпочтительнее переменные окружения.

`signing.properties` исключён через `.gitignore`. Пароли хранятся в нём открытым
текстом: не публикуй и не показывай файл. Если файл уже отслеживался Git,
одного `.gitignore` недостаточно. Для Linux ограничь доступ: `chmod 600 signing.properties`.

Альтернатива — пользовательский `~/.gradle/gradle.properties` с теми же свойствами
или переменные окружения `OLDYM_KEYSTORE`, `OLDYM_KEY_ALIAS`,
`OLDYM_KEYSTORE_PASSWORD`, `OLDYM_KEY_PASSWORD`.
Если пароль ключа не указан отдельно, используется пароль хранилища.
Значения паролей не передаются как аргументы командной строки и не печатаются сборкой.

Сборка:

```sh
./gradlew assembleRelease
```

```bat
gradlew.bat assembleRelease
```

Для обновления установленного клиента нужны тот же ID приложения, тот же ключ
и подходящий `versionCode` в исходном `AndroidManifest.xml`.
Не публикуй debug-ключ, свой release-ключ, пароли или токены.
Подпись — V1/JAR, `min-sdk-version` берётся из манифеста; V2 выключена ради
сохранения прежней схемы совместимости. Проверка подписи запускается автоматически.

## Репозиторий и проверка

В Git должны попасть оба Wrapper-скрипта и **`gradle/wrapper/gradle-wrapper.jar`**,
а также `gradle-wrapper.properties`, `settings.gradle`, `build.gradle`,
`gradle.properties` и документация. Не исключай все JAR глобальным правилом:
в проекте есть необходимые зависимости. `.gradle/`, `build/` и ключи исключены.

Linux executable bit для Git при необходимости:

```sh
git update-index --chmod=+x gradlew build.sh
```

Успешная сборка не доказывает работоспособность входа, аудио и скачивания:
проверяй APK на целевых устройствах. Итоги фактически выполненных проверок
этого комплекта находятся в `GRADLE-TEST-RESULTS.md`.

Наличие Gradle не требует менять лицензию приложения или повышать minSdk.
Этот комплект не заявляет, что любой новый AGP несовместим с API 5:
переход на AGP — отдельная миграция и отдельные проверки.
