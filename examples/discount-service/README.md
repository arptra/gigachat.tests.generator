# Discount Service Demo

Минимальный Gradle-проект, который использует генератор тестов.

## Структура

- `src/main/java/com/acme/discount/DiscountService.java` — начальный пример и точка входа в расширенную бизнес-логику.
- `src/main/java/com/acme/discount/DiscountEngine.java` — агрегатор правил скидок.
- `src/main/java/com/acme/discount/LoyaltyDiscountRule.java`, `BulkOrderDiscountRule.java`, `SeasonalDiscountRule.java` — примеры правил с ветвлениями и условиями.
- `src/main/java/com/acme/discount/CustomerProfile.java`, `Order.java` и сопутствующие классы — модели предметной области с валидацией и вспомогательными методами.
- `build.gradle` — конфигурация Java 17 и JUnit 5, Jacoco-отчёт подключён к задаче `test`.

## Запуск тестов

```bash
./gradlew --project-dir examples/discount-service test
```

Команда выше предполагает запуск из корня репозитория `gigachat.tests.generator` и использует уже настроенный Gradle Wrapper.
Для подробного вывода диагностики можно добавить флаг `--info`:

```bash
./gradlew --project-dir examples/discount-service test --info
```
