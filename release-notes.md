## MythicPassiveSkills 1.2.0

Старт и конец пассивки — новые триггеры `~onStartPassive` и `~onEndPassive`.

### Добавлено

- **`~onStartPassive` / `~onEndPassive`** — строки выполняются в момент включения
  и остановки пассивки, а не по событию игрока.
- **`?passive=<id>`** — фильтр по конкретной пассивке (`<имя_файла>:<ключ профиля>`).
  Без фильтра строка обрабатывает старт/конец любой пассивки игрока.
- `PassiveTrigger.parseExact()` — разбор триггера по точному имени, без
  неоднозначных совпадений между алиасами.

### Пример

```yaml
PassiveSkills:
  sneak_levitation:
    Permission: mythicpassives.levitation
    Skills:
      - "lunge{velocity=0.6} @self ~onTimer:1 ?sneaking ?!onGround"
      - "particle{particle=SPELL_WITCH} @self ~onStartPassive ?passive=example:sneak_levitation"
      - "cooldown{s=Fireball;seconds=5} @self ~onEndPassive ?passive=example:sneak_levitation"
```

### Когда срабатывает

| Триггер | Событие |
|---|---|
| `~onStartPassive` | профиль активирован: вход в игру, выдача права |
| `~onEndPassive` | снятие права, выход игрока, смена мира |

### Исправлено в 1.1.0 (вошло и сюда)

- `~onTimer` и `~onJump` больше не отбрасываются при разборе — пассивки с ними
  снова работают.
- Клики и бой слушаются на `MONITOR`: способности MythicCrucible предметов
  (`onLeftClick`, `onPressF`, `onUse`) не теряются и не дублируются.
- Маршрутизация упрощена до флага `passive-routing.item-triggers`.

### Установка

1. Положите `MythicPassiveSkills-1.2.0.jar` в `plugins/` (заменив старый).
2. Полностью перезапустите сервер (не `/reload`).
3. Если `plugins/MythicPassiveSkills/config.yml` остался от версии 1.0 —
   удалите его, будет создан актуальный.

Требования: Paper 1.20+, MythicMobs 5.x, Java 21.
