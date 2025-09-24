# Генератор модульных тестов на базе GigaChat

Этот проект содержит консольный агент, который анализирует исходный код Java-проекта, формирует запросы к GigaChat и сохраняет сгенерированные тесты в `src/test/java`. После записи файлов агент запускает Gradle-тесты и, при наличии Jacoco, прикладывает отчёт о покрытии.

## Требования

* Java 21 (JDK, а не только JRE).
* Доступ к API GigaChat и действующие учётные данные приложения.
* Поддерживаемый инструмент сборки в проекте-цели — Gradle с wrapper (`gradlew` или `gradlew.bat`).

## 1. Настройка доступа к GigaChat

### Получите OAuth-креденшлы

1. Зарегистрируйте приложение в личном кабинете GigaChat и запросите доступ к **Client Credentials**.
2. Сохраните базовый URL API, URL для получения токенов, `api_key` и параметры для mTLS (сертификат, приватный ключ, корневой сертификат).
3. При необходимости уточните scope и модель (если хотите отличные от стандартных `GIGACHAT_API_PERS` и `GigaChat`).

### Установите переменные окружения

Агент считывает настройки из переменных окружения, системных свойств JVM или файла `gradle.properties`. Минимальный набор:

```bash
export GIGACHAT_API_BASE="https://gigachat.devices.sberbank.ru/"
export GIGACHAT_AUTH_URL="https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
export GIGACHAT_API_KEY="<ваш_api_key>"
# Необязательно, но можно переопределить и дополнить
export GIGACHAT_SCOPE="GIGACHAT_API_PERS"
export GIGACHAT_MODEL="GigaChat-2-Max"
export GIGACHAT_CERT_FILE="/path/to/cert.pem"
export GIGACHAT_KEY_FILE="/path/to/key.key"
export GIGACHAT_CA_FILE="/path/to/ca.pem"
# Управление уровнем логирования агента (необязательно, значения как у java.util.logging.Level)
export GIGACHAT_AGENT_LOG_LEVEL="FINE"
```

Переменные можно задать и через `-D`-параметры JVM (например, `-DGIGACHAT_API_BASE=...`) или поместить в `gradle.properties` проекта. При запуске токен автоматически кэшируется и переиспользуется до истечения срока действия.

## 2. Запуск генератора

1. Соберите проект генератора:

   ```bash
   ./gradlew clean build
   ```

2. Запустите CLI, указав путь к проекту, для которого нужно сгенерировать тесты:

   ```bash
   java -jar build/libs/gigachat-tests-generator-1.0-SNAPSHOT.jar \
     --project /path/to/target-project \
     --limit 10 \
     --max-retries 2
   ```

3. После завершения работы агент запишет новые тесты в `src/test/java` целевого проекта и автоматически выполнит `./gradlew test` внутри него. В консоли вы увидите итоговый статус и путь к отчёту о покрытии, если Jacoco сформировал XML-файл.

### Параметры CLI

* `-p, --project <path>` — путь к корню проекта (по умолчанию текущая директория).
* `-c, --class <fqcn>` — полностью квалифицированное имя класса, который нужно покрыть (флаг можно повторять несколько раз). Без указания агент обрабатывает все найденные классы.
* `--limit <n>` — ограничение на количество классов за один запуск.
* `--max-retries <n>` — сколько раз можно отправлять уточнённый запрос в случае валидационных ошибок.
* `--gigachat-delay <s>` — задержка в секундах между повторными запросами в GigaChat (по умолчанию нет задержки).
* `-h, --help` — вывести справку по командам и завершить выполнение.

### Что делает агент внутри

1. **Сканирование проекта.** Сервис `ProjectScanner` проходит по исходникам, извлекает метаданные классов и методов и игнорирует каталоги `build`, `out`, `generated` и др.
2. **Формирование промпта.** По собранным метаданным строится промпт, который описывает целевой класс и требования к тестам.
3. **Запрос к GigaChat.** Клиент `GigachatLLMClient` отправляет промпт, применяет экспоненциальный бэкофф и автоматически обновляет OAuth-токен.
4. **Валидация ответа.** `ResponseValidator` проверяет, что в ответе есть ` ```java`-блок, код компилируется, содержит JUnit 5 аннотации и необходимые импорты.
5. **Запись и запуск тестов.** Тесты сохраняются в соответствующий пакет внутри `src/test/java`, затем выполняется `./gradlew test`. При успешной сборке дополнительно читается Jacoco-отчёт и вычисляется список классов без тестов.

## 3. Пример: покрываем сервис скидок

Ниже — реальный сценарий запуска на небольшом проекте `discount-service`, который содержится в каталоге [`examples/discount-service`](examples/discount-service) данного репозитория и включает класс `com.acme.discount.DiscountService` с логикой расчёта скидок.

1. Открываем каталог примера:

   ```bash
   cd examples/discount-service
   ```

2. Запускаем генератор (жар-файл берём из собранного артефакта в корне репозитория):

   ```bash
   java -jar ../../build/libs/gigachat-tests-generator-1.0-SNAPSHOT.jar \
     --project $(pwd) \
     --class com.acme.discount.DiscountService
   ```

3. Генератор сохранит тесты и выведет путь к Jacoco-отчёту:

   ```
   Tests generated successfully.
   Coverage report: /path/to/examples/discount-service/build/reports/jacoco/test/jacocoTestReport.xml
   ```

4. В каталоге `src/test/java/com/acme/discount` примера появился файл `DiscountServiceTest.java`. Его фрагмент:

   ```java
   package com.acme.discount;

   import org.junit.jupiter.api.Test;

   import static org.junit.jupiter.api.Assertions.assertEquals;

   class DiscountServiceTest {

       private final DiscountService service = new DiscountService();

       @Test
       void appliesLoyaltyDiscount() {
           assertEquals(90.0, service.applyDiscount(100.0, true));
       }

       @Test
       void skipsDiscountForNewCustomer() {
           assertEquals(100.0, service.applyDiscount(100.0, false));
       }
   }
   ```

5. Отчёт Jacoco показывает >80 % покрытие методов сервиса, а агент дополнительно перечислит классы, в которых тесты пока отсутствуют (если такие есть).

> Совет: добавьте шаг запуска агента в CI-пайплайн, чтобы регулярно обновлять покрытие. При необходимости можно создать отдельный Gradle-профиль, который генерирует Jacoco XML (например, задачей `jacocoTestReport`).
