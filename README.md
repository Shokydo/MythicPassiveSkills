# MythicPassiveSkills

Аддон для **MythicMobs**, который даёт игрокам пассивные навыки, привязанные к permission-нодам.
Пока у игрока есть право — пассивка работает; право сняли или игрок вышел — задача сразу останавливается.

## Возможности

- Пассивки в нативном формате MythicMobs: строки `Skills:` — это полные механики
  с таргетером, триггером и условиями.
- Режимы `interval` и `triggered` задаются прямо в строке:
  `~onTimer:40`, `~onSneak`, `~onAttack` и т.д.
- Несколько действий в одном профиле, у каждого свой триггер и условия.
- Привязка к одной или нескольким permission-нодам (достаточно любой).
- Обновление прав «на лету»: через LuckPerms-события и периодическую проверку.
- Поддержка старого формата (`Trigger`, `Interval`, `Conditions`, имена скиллов).
- Кастомная механика `passive{...}` для выдачи пассивки из любого скилла MythicMobs.
- Кастомные триггеры вида `~onSneakPassive`.
- Автоматическая остановка задач при выходе игрока, снятии права, выгрузке мира и `/mm reload`.
- Команда `/passive` для перезагрузки и просмотра профилей.

## Требования

| Компонент | Версия |
|---|---|
| Paper / Spigot | 1.20+ |
| MythicMobs | 5.x+ |
| Java | 21 |
| LuckPerms | опционально (для мгновенного обновления прав) |

## Установка

1. Соберите плагин или возьмите готовый jar.
2. Положите `MythicPassiveSkills-1.2.0.jar` в `plugins/`.
3. Запустите сервер. Появится папка `plugins/MythicMobs/PassiveSkills/`
   с файлом `example.yml`.
4. Создайте свои профили в `plugins/MythicMobs/PassiveSkills/*.yml`.
5. Выполните `/passive reload` (или `/mm reload`).

## Формат файла

```yaml
PassiveSkills:
  fire_aura:
    Enabled: true
    Permission: mythicpassives.fire
    Skills:
      - "skill{s=FireAura} @self ~onTimer:40 ?health{h=>0}"
  poison_blade:
    Permission: mythicpassives.poison
    Skills:
      - "skill{s=PoisonBlade} @trigger ~onAttack"
```

Профили можно писать и в корне файла, без секции `PassiveSkills:`.

| Ключ | Обязателен | Описание |
|---|---|---|
| `Enabled` | нет | `true`/`false`, по умолчанию `true`. |
| `Permission` / `Permissions` | нет | Нода или список нод. Если не задано — пассивка доступна всем. |
| `Skills` / `Skill` | да | Список полных строк MythicMobs (см. ниже). |
| `Trigger` | нет | Триггер по умолчанию для строк без `~триггера`. |
| `Interval` | нет | Интервал по умолчанию для строк без `~onTimer:<тики>`. |
| `Conditions` / `Condition` | нет | Дополнительные условия профиля (старый формат). |

ID профиля в командах: `<имя_файла_без_yml>:<ключ>`, например `example:fire_aura`.

### Строки `Skills:`

Каждая строка — обычная строка скилла MythicMobs:

```text
<механика> @таргет ~триггер ?условие
```

Примеры:

```yaml
Skills:
  - "skill{s=FireAura} @self ~onTimer:40 ?health{h=>0}"
  - "skill{s=PoisonBlade} @trigger ~onAttack"
  - "skill{s=ShadowStep} @self ~onSneak"
  - "potion{type=SPEED;duration=100;level=1} @self ~onTimer:100"
```

- `~onTimer:<тики>` — периодическое выполнение (20 тиков = 1 секунда).
- `~onSneak`, `~onAttack`, `~onDamaged`, `~onJump` и другие — событийные триггеры.
- `?условие` — стандартное условие MythicMobs. Проверяется перед каждым срабатыванием.
- `@self`, `@trigger` и остальные таргетеры MythicMobs работают как обычно.

Если в строке нет триггера, используются `Trigger` и `Interval` профиля
(по умолчанию `interval` и 20 тиков). Это позволяет постепенно переходить
со старого формата на новый.

### Старый формат

Старые профили продолжают работать без изменений:

```yaml
PassiveSkills:
  fire_aura:
    Enabled: true
    Permission: mythicpassives.fire
    Trigger: interval
    Interval: 40
    Skills:
      - FireAura
    Conditions:
      - "?health{h=>0}"
```

В этом случае `Trigger` и `Interval` задают режим, а `Skills` — имена скиллов
(можно несколько через запятую). Условия из `Conditions` проверяются перед
каждым срабатыванием.

### Триггеры

| Триггер в строке | Когда срабатывает |
|---|---|
| `~onTimer:<тики>` | Каждые N тиков. |
| `~onSneak` / `~onUnSneak` | Игрок присел / встал. |
| `~onAttack` | Игрок нанёс урон (цель доступна через `@trigger`). |
| `~onDamaged` | Игрок получил урон. |
| `~onJump` | Прыжок (только Paper). |
| `~onKill` | Игрок убил сущность. |
| `~onDeath` / `~onRespawn` | Смерть / возрождение игрока. |
| `~onBlockBreak` / `~onBlockPlace` | Ломает / ставит блок. |
| `~onInteract` | Правый клик. |
| `~onShoot` | Выстрел из лука. |
| `~onConsume` | Съел / выпил предмет. |
| `~onWorldChange` | Смена мира. |
| `~onFish` | Поймал рыбу или сущность. |

Дополнительно зарегистрированы «пассивные» варианты:
`~onSneakPassive`, `~onAttackPassive`, `~onDamagedPassive` и т.д.
Для таймера можно писать `~onTimer:40`, `~onInterval:40` или `~onTick:40` —
это одно и то же.

### Старт и конец пассивки

У пассивки нет одного общего «включилась» — каждая строка `Skills:` живёт
своим триггером. Так что события старта и конца вешаются строками
`~onStartPassive` / `~onEndPassive` в любом профиле игрока:

```yaml
PassiveSkills:
  levitation:
    Permission: mythicpassives.levitation
    Skills:
      - "lunge{} @self ~onTimer:1 ?sneaking ?!onGround"
      - "particle{particle=SPELL_WITCH} @self ~onStartPassive ?passive=passive:levitation"
      - "particle{particle=SMOKE} @self ~onEndPassive ?passive=passive:levitation"
```

| Триггер | Когда срабатывает |
|---|---|
| `~onStartPassive` | профиль активирован: вход в игру, выдача права |
| `~onEndPassive` | профиль остановлен: снятие права, выход, смена мира |

Фильтр можно писать двумя равнозначными способами:
`~onEndPassive{passive=example:levitation}` или `~onEndPassive ?passive=example:levitation`
(несколько ID — через запятую). Без фильтра строка
обрабатывает включение/выключение любой пассивки игрока (удобно для общих
кулдаунов вида `cooldown{s=Fireball} @self ~onEndPassive`).

ID пассивки — `<имя_файла>:<ключ профиля>`: для `example.yml` и ключа
`levitation` это `example:levitation`.

Строки `~onEndPassive` самой выключаемой пассивки не выполняются (она уже
остановлена); обработчики из других профилей и общие строки получают событие
до окончательного удаления профиля.

### Совместимость с MythicCrucible

Клик по предмету хотят обработать сразу три плагина: MythicCrucible
(`onLeftClick`, `onPressF`, `onUse` предмета), MythicMobs (`onUse` предмета)
и этот аддон. Правила:

- **Триггеры MythicCrucible не перезаписываются.** Аддон регистрирует свои
  триггеры только после включения MythicMobs и MythicCrucible, а имена вроде
  `onUse`, `onLeftClick`, `onBreak`, `onPickup` не занимает никогда
  (для них есть `PASSIVE_*` варианты).
- **Все клики и боевые события слушаются на `MONITOR`.** Аддон ничего не
  отменяет раньше других плагинов — поэтому способность предмета получает событие.
- **С предметом в руках пассивка работает параллельно** с предметом
  MythicCrucible (`passive-routing.item-triggers: true`) — оба получают клик.
  Если нужно, чтобы при конфликте побеждал предмет, поставьте `false`:
  тогда пассивка молчит именно на этом предмете и только для того триггера,
  который у предмета уже объявлен.
- **Без предмета в руках пассивки работают всегда** — проверки предмета
  выполняются только когда в руке Mythic-предмет.
- **Свап рук (F)** отменяется только если F используется способностью
  (пассивкой или `onPressF` предмета). Иначе свап остаётся ванильным.
- **Правый клик** отменяется только чтобы MythicMobs запустил `onUse`
  предмета ровно один раз.
- **Левый клик** приходит двумя событиями (`PlayerInteractEvent` и
  `PlayerAnimationEvent`) — пассивка запускается один раз за тик, а анимация
  не отменяется (она нужна MythicCrucible для `onLeftClick`).

Все настройки — в секции `passive-routing` файла `config.yml`.
Применяются после `/passive reload` или перезапуска сервера.

### Условия

Условия — это стандартные условия MythicMobs. Их можно писать прямо в строке:

```yaml
Skills:
  - "skill{s=FireAura} @self ~onTimer:40 ?health{h=>5}"
  - "skill{s=ShadowStep} @self ~onSneak ?sneaking"
```

Дополнительно можно указать условия на уровне профиля (старое поле `Conditions`).
Они проверяются перед каждым действием профиля.

### Цели скилла

По умолчанию цели — сам игрок:

- `@self` / `@target` — игрок;
- `@trigger` — сущность из события (например, цель атаки).

Пример пассивки на атаку:

```yaml
PassiveSkills:
  poison_blade:
    Permission: mythicpassives.poison
    Skills:
      - "skill{s=PoisonBlade} @trigger ~onAttack"
```

```yaml
# plugins/MythicMobs/Skills/example.yml
PoisonBlade:
  Skills:
    - potion{type=POISON;duration=100;level=1} @target
```

## Механика `passive{...}`

Механика выдаёт пассивку из любого скилла MythicMobs. Повторный вызов с теми же
параметрами не создаёт дубликат.

```yaml
GrantFirePassive:
  Skills:
    - passive{permission=mythicpassives.fire;skill=FireAura;interval=40;trigger=interval} @self
```

Можно передать и полную строку:

```yaml
GrantFirePassive:
  Skills:
    - passive{permission=mythicpassives.fire;skill=skill{s=FireAura} @self ~onTimer:40} @self
```

Параметры:

| Параметр | Описание |
|---|---|
| `permission` / `permissions` | Нода или список нод. |
| `skill` / `skills` | Имя скилла, несколько имён через запятую или полная строка MythicMobs. |
| `interval` | Период в тиках для `trigger=interval`. |
| `trigger` | `interval`, `sneak`, `attack`, `jump` и т.д. |
| `conditions` / `condition` | Необязательные условия MythicMobs. |

Если указать право, которого у игрока нет, механика вернёт `CONDITION_FAILED`
и не запустит пассивку.

> Механика реализована как `ITargetedEntitySkill`, поэтому в строке скилла
> нужно явно указать цель игрока: `passive{...} @self`. Если скилл и так
> применяется к игроку (например, вызывается механикой `sudo` с `@target`),
> дополнительный таргетер не обязателен.

## Кастомные триггеры MythicMobs

Плагин регистрирует триггеры, которые можно использовать в скиллах:

```yaml
ExampleSkill:
  Skills:
    - potion{type=SPEED;duration=100;level=1} @self ~onSneakPassive
```

Основные имена: `PASSIVE_START`, `PASSIVE_END`, `PASSIVE_INTERVAL`,
`PASSIVE_SNEAK`, `PASSIVE_UNSNEAK`,
`PASSIVE_ATTACK`, `PASSIVE_DAMAGED`, `PASSIVE_JUMP`, `PASSIVE_KILL`,
`PASSIVE_DEATH`, `PASSIVE_RESPAWN`, `PASSIVE_BLOCK_BREAK`,
`PASSIVE_BLOCK_PLACE`, `PASSIVE_INTERACT`, `PASSIVE_SHOOT`,
`PASSIVE_CONSUME`, `PASSIVE_WORLD_CHANGE`, `PASSIVE_FISH`.

## Команды

| Команда | Описание |
|---|---|
| `/passive reload` | Перечитать конфиг и файлы пассивок. |
| `/passive list` | Список профилей. |
| `/passive info <id>` | Информация о профиле. |
| `/passive info` | Свои активные пассивки. |
| `/passive player <ник>` | Активные пассивки игрока. |

Право: `mythicpassiveskills.admin` (по умолчанию только у операторов).

## Обновление прав

- При установленном LuckPerms плагин подписывается на `UserDataRecalculateEvent` и
  `NodeMutateEvent` — пассивки включаются и выключаются сразу после изменения прав.
- Дополнительно каждые `settings.permission-refresh-ticks` тиков выполняется проверка
  всех онлайн-игроков. Это работает и без LuckPerms.
- Перед каждым interval-срабатыванием право проверяется ещё раз
  (`settings.recheck-permission-each-tick`).

## Перезагрузка

- `/passive reload` — перечитывает `config.yml` и `PassiveSkills/*.yml`.
- `/mm reload` — MythicMobs перечитывает скиллы, после чего плагин автоматически
  перечитывает профили (`MythicPostReloadedEvent`).

## Сборка

```bash
mvn package
```

По умолчанию jar MythicMobs берётся из
`D:\_SERVERS\_MYTEST\plugins\MythicMobs-5.13.0.jar`. Другой путь можно указать так:

```bash
mvn package -Dmythicmobs.jar="C:\path\to\MythicMobs.jar"
```

Готовый файл: `target/MythicPassiveSkills-1.2.0.jar`.

## Замечания

- `PlayerJumpEvent` есть только на Paper. На Spigot триггер `jump` просто не регистрируется.
- Пассивки не переживают выход игрока: при следующем входе они включатся заново
  (для файловых профилей — автоматически, для механики — при следующем выполнении
  родительского скилла).
- После `/mm reload` и `/passive reload` активные пассивки останавливаются и
  пересоздаются из файлов. Пассивки, выданные механикой `passive{...}`, снова
  включатся только после повторного выполнения родительского скилла.
- При выгрузке мира задачи игроков в этом мире останавливаются.
- Рекурсивные срабатывания защищены: пока выполняется действие пассивки, новые события
  того же игрока не запускают пассивки повторно.
- При срабатывании пассивки по клику на предмет в `SkillMetadata` подменяется
  имя предмета (`passive-routing.resolve-item-use-loop`). Это не даёт
  MythicCrucible запустить способность предмета повторно через `onUse`,
  пока пассивка выполняется.
