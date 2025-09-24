# Discount Service Demo

Минимальный Gradle-проект, который использует генератор тестов.

## Структура

- `src/main/java/com/acme/discount/DiscountService.java` — простая бизнес-логика.
- `build.gradle` — конфигурация Java 17 и JUnit 5, Jacoco-отчёт подключён к задаче `test`.

## Запуск тестов

```bash
./gradlew --project-dir examples/discount-service test
```

Команда выше предполагает запуск из корня репозитория `gigachat.tests.generator` и использует уже настроенный Gradle Wrapper.
