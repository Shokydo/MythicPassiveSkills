# Changelog

Все заметные изменения проекта. Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версии — [SemVer](https://semver.org/lang/ru/).

## [1.1.0]

Версия посвящена совместимости с **MythicCrucible**: способности предметов на
`onLeftClick`, `onPressF` и `onUse` больше не теряются и не дублируются.

### Исправлено

- **Триггеры пассивок не разбирались.** `~onTimer` и `~onJump` возвращали `null`
  в `PassiveTrigger.parse()`, и `PassiveActionFactory` выбрасывал всю строку
  `Skills:` — пассивки с этими триггерами не работали вовсе.
  - алиасы `ONTIMER`, `ONJUMP` (и другие ключевые) возвращены в таблицу `enum`;
  - добавлены `BY_RESERVED` и `guessReserved()` — разбор по точному имени даже
    если алиас пропал из `enum`;
  - `normalize()` понимает `~onTimer`, `on_timer`, `ONTIMER:20`;
  - исправлена ловушка префиксов: `onUse` → `RIGHT_CLICK`, `onLeftClick` → `LEFT_CLICK`
    (раньше оба уходили в `ATTACK`).
- **Незарегистрированный `SkillTrigger` больше не отключает действие** — берётся
  `PASSIVE_INTERVAL` как безопасный фолбэк.
- **Пассивки отключались для любого предмета в руке.** Условие
  «не дублировать предмет» было слишком широким и гасило боевые триггеры.
  Теперь маршрутизация упрощена до одного флага.

### Изменено

- `PassiveAction.TriggerRouting` — вместо четырёх полей один флаг `itemTriggers`:
  `true` — пассивка работает параллельно с предметом, `false` — при конфликте
  побеждает предмет.
- `PassiveTriggerListener`:
  - события слушаются на `MONITOR`, аддон не перехватывает их раньше других плагинов;
  - левый клик приходит дважды (`PlayerInteractEvent` + `PlayerAnimationEvent`) —
    пассивка запускается один раз за тик;
  - F гасится, только если F реально используется способностью;
  - правый клик отменяется лишь чтобы `onUse` предмета сработал один раз.
- `config.yml`: секция `passive-routing` упрощена (`item-triggers`, `item-use-click`,
  `cancel-swap-hands`, `resolve-item-use-loop`, `refresh-ticks`).
- `plugin.yml`: добавлены `softdepend` на `MythicCrucible`, `MythicEnchants`,
  `PlaceholderAPI`; триггеры регистрируются после включения MythicCrucible.
- `TriggerSourceCache`: кэш триггеров предметов, перечитывается после `/mm reload`
  и по таймеру.
- `PassiveManager.fire(...)` и `SkillCasterService.cast(...)` возвращают `boolean`.

### Документация

- `docs/wiki.html`: разделы «Настройки (config.yml)», «Если не работает»,
  «Зарезервированные имена», пример расы и алиасы триггеров.
- `README.md`: раздел совместимости с MythicCrucible.

## [1.0.0]

- Первый выпуск: пассивные навыки MythicMobs, привязанные к permission-нодам,
  механика `passive{...}`, кастомные триггеры, интеграция с LuckPerms,
  команда `/passive`.
