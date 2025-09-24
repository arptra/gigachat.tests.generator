# Генератор модульных тестов на базе GigaChat

Этот проект содержит консольный агент, который анализирует исходный код Java-проекта, формирует запросы к GigaChat и сохраняет сгенерированные тесты в `src/test/java`. После записи файлов агент запускает Gradle-тесты и, при наличии Jacoco, прикладывает отчёт о покрытии.

## Требования

* Java 21 (JDK, а не только JRE).
* Доступ к API GigaChat и действующие учётные данные приложения.
* Поддерживаемый инструмент сборки в проекте-цели — Gradle с wrapper (`gradlew` или `gradlew.bat`).

## 1. Настройка доступа к GigaChat

### Получите OAuth-креденшлы

1. Зарегистрируйте приложение в личном кабинете GigaChat и запросите доступ к **Client Credentials**.
2. Сохраните значение `client_id`, `client_secret`, URL для получения токенов и базовый URL API.
3. При необходимости уточните scope и модель (если хотите отличные от стандартных `GIGACHAT_API_PERS` и `GigaChat`).

### Установите переменные окружения

Агент считывает настройки из переменных окружения или системных свойств JVM. Минимальный набор:

```bash
export GIGACHAT_BASE_URL="https://gigachat.sberdevices.ru/api/v1"
export GIGACHAT_AUTH_URL="https://auth.sberdevices.ru/as/token.oauth2"
export GIGACHAT_CLIENT_ID="<ваш_client_id>"
export GIGACHAT_CLIENT_SECRET="<ваш_client_secret>"
# Необязательно, но можно переопределить
export GIGACHAT_SCOPE="GIGACHAT_API_PERS"
export GIGACHAT_MODEL="GigaChat"
```

Переменные можно задать и через `-D`-параметры JVM (например, `-DGIGACHAT_BASE_URL=...`). При запуске токен автоматически кэшируется и переиспользуется до истечения срока действия.

## 2. Запуск генератора

1. Соберите проект генератора:

   ```bash
   ./gradlew clean build
   ```

2. Запустите CLI, указав путь к проекту, для которого нужно сгенерировать тесты:

   ```bash
   java -cp build/libs/gigachat.tests.generator-1.0-SNAPSHOT.jar \
     com.example.tests.generator.cli.TestGeneratorCli \
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
* `-h, --help` — вывести справку по командам и завершить выполнение.

### Что делает агент внутри

1. **Сканирование проекта.** Сервис `ProjectScanner` проходит по исходникам, извлекает метаданные классов и методов и игнорирует каталоги `build`, `out`, `generated` и др.
2. **Формирование промпта.** По собранным метаданным строится промпт, который описывает целевой класс и требования к тестам.
3. **Запрос к GigaChat.** Клиент `GigachatLLMClient` отправляет промпт, применяет экспоненциальный бэкофф и автоматически обновляет OAuth-токен.
4. **Валидация ответа.** `ResponseValidator` проверяет, что в ответе есть ` ```java`-блок, код компилируется, содержит JUnit 5 аннотации и необходимые импорты.
5. **Запись и запуск тестов.** Тесты сохраняются в соответствующий пакет внутри `src/test/java`, затем выполняется `./gradlew test`. При успешной сборке дополнительно читается Jacoco-отчёт и вычисляется список классов без тестов.

## 3. Пример: покрываем сервис скидок

Ниже — реальный сценарий запуска на небольшом проекте `discount-service`, который содержит класс `com.acme.discount.DiscountService` с логикой расчёта скидок.

1. Клонируем проект и убеждаемся, что в нём есть Gradle wrapper:

   ```bash
   git clone https://github.com/acme-labs/discount-service.git
   cd discount-service
   ```

2. Запускаем генератор (предполагается, что репозиторий агента находится рядом):

   ```bash
   java -cp ../gigachat.tests.generator/build/libs/gigachat.tests.generator-1.0-SNAPSHOT.jar \
     com.example.tests.generator.cli.TestGeneratorCli \
     --project $(pwd) \
     --class com.acme.discount.DiscountService
   ```

3. Консольный вывод:

   ```
   Tests generated successfully.
   Coverage report: /home/user/discount-service/build/reports/jacoco/test/jacocoTestReport.xml
   ```

4. В каталоге `src/test/java/com/acme/discount` появился файл `DiscountServiceTest.java`. Его фрагмент:

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
