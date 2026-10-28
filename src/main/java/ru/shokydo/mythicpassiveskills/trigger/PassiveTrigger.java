package ru.shokydo.mythicpassiveskills.trigger;

import io.lumine.mythic.api.skills.SkillTrigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Триггеры пассивных навыков.
 * <p>
 * Для каждого значения регистрируется собственный {@link SkillTrigger}
 * MythicMobs
 * (вместе со всеми алиасами), поэтому в скиллах можно использовать как длинные
 * имена ({@code ~onSneakPassive}), так и короткие ({@code ~onSneak},
 * {@code ~onPressF}, {@code ~onPress1}, {@code ~onHold} и т.д.) —
 * в стиле триггеров предметов MythicMobs/MythicCrucible.
 * <p>
 * Регистрация идёт через {@code putIfAbsent}, поэтому конфликты с уже
 * зарегистрированными именами невозможны — существующее имя всегда выигрывает.
 * Именно поэтому {@link #registerAll()} вызывается не в {@code onLoad()},
 * а после включения MythicMobs и MythicCrucible: иначе мы могли бы первыми
 * занять имя вроде {@code onUse} и забрать обработку клика у MythicCrucible.
 */
public enum PassiveTrigger {

        // ------------------------------------------------------------
        // Базовые триггеры
        // ------------------------------------------------------------
        INTERVAL("PASSIVE_INTERVAL",
                        "PASSIVETICK", "TICKPASSIVE", "ONINTERVALPASSIVE", "INTERVALPASSIVE",
                        "INTERVAL", "ONINTERVAL", "TICK", "ONTICK", "TIMER", "ONTIMER"),

        SNEAK("PASSIVE_SNEAK",
                        "SNEAKPASSIVE", "PASSIVESNEAK", "ONSNEAKPASSIVE",
                        "SNEAK", "ONSNEAK", "SNEAKING", "ONSNEAKING", "CROUCH", "ONCROUCH",
                        "ONSNEAKFULL", "SNEAKFULL", "ONSNEAKTRUE", "SNEAKTRUE"),

        UNSNEAK("PASSIVE_UNSNEAK",
                        "UNSNEAKPASSIVE", "PASSIVEUNSNEAK", "ONUNSNEAKPASSIVE",
                        "UNSNEAK", "ONUNSNEAK", "UNCROUCH", "ONUNCROUCH", "STANDUP", "ONSTANDUP",
                        "ONSNEAKFALSE", "SNEAKFALSE"),

        ATTACK("PASSIVE_ATTACK",
                        "ATTACKPASSIVE", "PASSIVEATTACK", "ONATTACKPASSIVE",
                        "HIT", "ONHIT", "STRIKE", "ONSTRIKE",
                        "ONATTACKPHYSICAL", "ATTACKPHYSICAL", "ONPHYSICALATTACK",
                        "ONBOWHIT", "BOWHIT", "ONBOWLAND", "BOWLAND"),

        DAMAGED("PASSIVE_DAMAGED",
                        "DAMAGEDPASSIVE", "PASSIVEDAMAGED", "ONDAMAGEDPASSIVE",
                        "DAMAGED", "ONDAMAGED", "HURT", "ONHURT",
                        "ONDAMAGEDPHYSICAL", "DAMAGEDPHYSICAL", "ONPHYSICALDAMAGED"),

        JUMP("PASSIVE_JUMP",
                        "JUMPPASSIVE", "PASSIVEJUMP", "ONJUMPPASSIVE",
                        "JUMP", "ONJUMP"),

        /**
         * Пробел (прыжок). Алиасы {@code ~onPressSpace}, {@code ~onSpace} и т.п.
         * срабатывают в тот же момент, что и {@code ~onJump}.
         */
        PRESS_SPACE("PASSIVE_PRESS_SPACE",
                        "PRESSSPACEPASSIVE", "PASSIVEPRESSSPACE", "ONPRESSSPACEPASSIVE",
                        "PRESSSPACE", "ONPRESSSPACE", "SPACE", "ONSPACE",
                        "PRESSJUMP", "ONPRESSJUMP", "JUMPKEY", "ONJUMPKEY",
                        "PRESSJUMPSPACE", "ONPRESSJUMPSPACE"),

        KILL("PASSIVE_KILL",
                        "KILLPASSIVE", "PASSIVEKILL", "ONKILLPASSIVE",
                        "KILL", "ONKILL", "PLAYERKILL", "ONPLAYERKILL"),

        DEATH("PASSIVE_DEATH",
                        "DEATHPASSIVE", "PASSIVEDEATH", "ONDEATHPASSIVE",
                        "DEATH", "ONDEATH", "PLAYERDEATH", "ONPLAYERDEATH"),

        RESPAWN("PASSIVE_RESPAWN",
                        "RESPAWNPASSIVE", "PASSIVERESPAWN", "ONRESPAWNPASSIVE",
                        "RESPAWN", "ONRESPAWN"),

        BLOCK_BREAK("PASSIVE_BLOCK_BREAK",
                        "BLOCKBREAKPASSIVE", "PASSIVEBLOCKBREAK", "ONBLOCKBREAKPASSIVE",
                        "BREAKBLOCK", "ONBREAKBLOCK"),

        BLOCK_PLACE("PASSIVE_BLOCK_PLACE",
                        "BLOCKPLACEPASSIVE", "PASSIVEBLOCKPLACE", "ONBLOCKPLACEPASSIVE",
                        "BLOCKPLACE", "ONBLOCKPLACE", "PLACEBLOCK", "ONPLACEBLOCK"),

        INTERACT("PASSIVE_INTERACT",
                        "INTERACTPASSIVE", "PASSIVEINTERACT", "ONINTERACTPASSIVE",
                        "INTERACT", "ONINTERACT"),

        SHOOT("PASSIVE_SHOOT",
                        "SHOOTPASSIVE", "PASSIVESHOOT", "ONSHOOTPASSIVE",
                        "SHOOT", "ONSHOOT", "SHOOTBOW", "ONSHOOTBOW",
                        "ONSHOT", "SHOT", "ONSHOTFIRED", "SHOTFIRED", "ONBOWSHOOT", "BOWSHOOT",
                        "ONBOWFIRE", "BOWFIRE", "ONBOWSHOT", "BOWSHOT"),

        CONSUME("PASSIVE_CONSUME",
                        "CONSUMEPASSIVE", "PASSIVECONSUME", "ONCONSUMEPASSIVE",
                        "CONSUME", "ONCONSUME", "EAT", "ONEAT", "DRINK", "ONDRINK"),

        WORLD_CHANGE("PASSIVE_WORLD_CHANGE",
                        "WORLDCHANGEPASSIVE", "PASSIVEWORLDCHANGE", "ONWORLDCHANGEPASSIVE",
                        "WORLDCHANGE", "ONWORLDCHANGE", "CHANGEWORLD", "ONCHANGEWORLD"),

        FISH("PASSIVE_FISH",
                        "FISHPASSIVE", "PASSIVEFISH", "ONFISHPASSIVE",
                        "FISH", "ONFISH"),

        // ------------------------------------------------------------
        // Триггеры нажатия кнопок (стиль MythicCrucible)
        // ------------------------------------------------------------
        /**
         * Левый клик по воздуху/блоку ({@code LEFT_CLICK_AIR},
         * {@code LEFT_CLICK_BLOCK}).
         */
        LEFT_CLICK("PASSIVE_LEFT_CLICK",
                        "LEFTCLICKPASSIVE", "PASSIVELEFTCLICK", "ONLEFTCLICKPASSIVE",
                        "LEFTCLICKAIR", "ONLEFTCLICKAIR",
                        "LEFTCLICKBLOCK", "ONLEFTCLICKBLOCK", "PRESSLEFT", "ONPRESSLEFT"),

        /**
         * Левая кнопка мыши как кнопка действия (стиль MythicCrucible
         * {@code onLeftClick}).
         * Отдельное имя нужно для умолчания в предметах: их {@code onLeftClick}
         * обрабатывает MythicCrucible, и пассивка не должна его перекрывать.
         */
        PRIMARY_CLICK("PASSIVE_PRIMARY_CLICK",
                        "PRIMARYCLICKPASSIVE", "PASSIVEPRIMARYCLICK", "ONPRIMARYCLICKPASSIVE",
                        "PRIMARYCLICK", "ONPRIMARYCLICK", "LEFTPRESS", "ONLEFTPRESS",
                        "PRESSLMB", "ONPRESSLMB", "LMB", "ONLMB"),

        RIGHT_CLICK("PASSIVE_RIGHT_CLICK",
                        "RIGHTCLICKPASSIVE", "PASSIVERIGHTCLICK", "ONRIGHTCLICKPASSIVE",
                        "RIGHTCLICK", "ONRIGHTCLICK", "RIGHTCLICKAIR", "ONRIGHTCLICKAIR",
                        "RIGHTCLICKBLOCK", "ONRIGHTCLICKBLOCK", "PRESSRIGHT", "ONPRESSRIGHT"),

        OFFHAND_CLICK("PASSIVE_OFFHAND_CLICK",
                        "OFFHANDCLICKPASSIVE", "PASSIVEOFFHANDCLICK", "ONOFFHANDCLICKPASSIVE",
                        "OFFHANDCLICK", "ONOFFHANDCLICK",
                        "INTERACTOFFHAND", "ONINTERACTOFFHAND", "OFFHANDINTERACT", "ONOFFHANDINTERACT"),

        PRESS_F("PASSIVE_PRESS_F",
                        "PRESSFPASSIVE", "PASSIVEPRESSF", "ONPRESSFPASSIVE",
                        "PRESSF", "ONPRESSF",
                        "SWAPHANDS", "ONSWAPHANDS", "SWAP", "ONSWAP", "HANDSWAP", "ONHANDSWAP",
                        "SWAPH", "ONSWAPH"),

        PRESS_Q("PASSIVE_PRESS_Q",
                        "PRESSQPASSIVE", "PASSIVEPRESSQ", "ONPRESSQPASSIVE",
                        "PRESSQ", "ONPRESSQ",
                        "PRESSDROP", "ONPRESSDROP"),

        PRESS_E("PASSIVE_PRESS_E",
                        "PRESSEPASSIVE", "PASSIVEPRESSE", "ONPRESSEPASSIVE",
                        "PRESSE", "ONPRESSE",
                        "INVENTORY", "ONINVENTORY", "OPENINVENTORY", "ONOPENINVENTORY",
                        "PRESSINVENTORY", "ONPRESSINVENTORY"),

        PRESS_T("PASSIVE_PRESS_T",
                        "PRESSTPASSIVE", "PASSIVEPRESST", "ONPRESSTPASSIVE",
                        "PRESST", "ONPRESST",
                        "CHAT", "ONCHAT", "PRESSCHAT", "ONPRESSCHAT"),

        PRESS1("PASSIVE_PRESS_1",
                        "PRESS1PASSIVE", "PASSIVEPRESS1", "ONPRESS1PASSIVE",
                        "PRESS1", "ONPRESS1", "HOTBAR1", "ONHOTBAR1"),

        PRESS2("PASSIVE_PRESS_2",
                        "PRESS2PASSIVE", "PASSIVEPRESS2", "ONPRESS2PASSIVE",
                        "PRESS2", "ONPRESS2", "HOTBAR2", "ONHOTBAR2"),

        PRESS3("PASSIVE_PRESS_3",
                        "PRESS3PASSIVE", "PASSIVEPRESS3", "ONPRESS3PASSIVE",
                        "PRESS3", "ONPRESS3", "HOTBAR3", "ONHOTBAR3"),

        PRESS4("PASSIVE_PRESS_4",
                        "PRESS4PASSIVE", "PASSIVEPRESS4", "ONPRESS4PASSIVE",
                        "PRESS4", "ONPRESS4", "HOTBAR4", "ONHOTBAR4"),

        PRESS5("PASSIVE_PRESS_5",
                        "PRESS5PASSIVE", "PASSIVEPRESS5", "ONPRESS5PASSIVE",
                        "PRESS5", "ONPRESS5", "HOTBAR5", "ONHOTBAR5"),

        PRESS6("PASSIVE_PRESS_6",
                        "PRESS6PASSIVE", "PASSIVEPRESS6", "ONPRESS6PASSIVE",
                        "PRESS6", "ONPRESS6", "HOTBAR6", "ONHOTBAR6"),

        PRESS7("PASSIVE_PRESS_7",
                        "PRESS7PASSIVE", "PASSIVEPRESS7", "ONPRESS7PASSIVE",
                        "PRESS7", "ONPRESS7", "HOTBAR7", "ONHOTBAR7"),

        PRESS8("PASSIVE_PRESS_8",
                        "PRESS8PASSIVE", "PASSIVEPRESS8", "ONPRESS8PASSIVE",
                        "PRESS8", "ONPRESS8", "HOTBAR8", "ONHOTBAR8"),

        PRESS9("PASSIVE_PRESS_9",
                        "PRESS9PASSIVE", "PASSIVEPRESS9", "ONPRESS9PASSIVE",
                        "PRESS9", "ONPRESS9", "HOTBAR9", "ONHOTBAR9"),

        HOTBAR_SLOT("PASSIVE_HOTBAR_SLOT",
                        "HOTBARSLOTPASSIVE", "PASSIVEHOTBARSLOT", "ONHOTBARSLOTPASSIVE",
                        "HOTBAR", "ONHOTBAR", "HOTBARSLOT", "ONHOTBARSLOT",
                        "PRESSHOTBAR", "ONPRESSHOTBAR", "SLOTCHANGE", "ONSLOTCHANGE"),

        // ------------------------------------------------------------
        // Триггеры предметов (стиль MythicCrucible)
        // ------------------------------------------------------------
        HOLD("PASSIVE_HOLD",
                        "HOLDPASSIVE", "PASSIVEHOLD", "ONHOLDPASSIVE",
                        "HOLD", "ONHOLD", "HOLDING", "ONHOLDING",
                        "HOLDITEM", "ONHOLDITEM", "HELD", "ONHELD", "HELDITEM", "ONHELDITEM"),

        EQUIP("PASSIVE_EQUIP",
                        "EQUIPPASSIVE", "PASSIVEEQUIP", "ONEQUIPPASSIVE",
                        "EQUIP", "ONEQUIP", "EQUIPARMOR", "ONEQUIPARMOR",
                        "ONARMOREQUIP", "ARMOREQUIP", "ONARMOREQUIPPED", "ARMOREQUIPPED"),

        UNEQUIP("PASSIVE_UNEQUIP",
                        "UNEQUIPPASSIVE", "PASSIVEUNEQUIP", "ONUNEQUIPPASSIVE",
                        "UNEQUIP", "ONUNEQUIP", "UNEQUIPARMOR", "ONUNEQUIPARMOR",
                        "ONARMORUNEQUIP", "ARMORUNEQUIP", "ONARMORUNEQUIPPED", "ARMORUNEQUIPPED"),

        PICKUP("PASSIVE_PICKUP",
                        "PICKUPPASSIVE", "PASSIVEPICKUP", "ONPICKUPPASSIVE",
                        "PICKUPITEM", "ONPICKUPITEM",
                        "ONITEMPICKUP", "ITEMPICKUP", "COLLECT", "ONCOLLECT"),

        THROW("PASSIVE_THROW",
                        "THROWPASSIVE", "PASSIVETHROW", "ONTHROWPASSIVE",
                        "THROW", "ONTHROW", "THROWITEM", "ONTHROWITEM",
                        "ONPROJECTILE", "PROJECTILELAUNCH", "ONPROJECTILELAUNCH",
                        "ONLAUNCH", "LAUNCH"),

        // ------------------------------------------------------------
        // Прочие триггеры
        // ------------------------------------------------------------
        SPRINT_START("PASSIVE_SPRINT_START",
                        "SPRINTSTARTPASSIVE", "PASSIVESPRINTSTART", "ONSPRINTSTARTPASSIVE",
                        "SPRINTSTART", "ONSPRINTSTART", "SPRINT", "ONSPRINT",
                        "STARTSPRINT", "ONSTARTSPRINT", "SPRINTON", "ONSPRINTON"),

        SPRINT_STOP("PASSIVE_SPRINT_STOP",
                        "SPRINTSTOPPASSIVE", "PASSIVESPRINTSTOP", "ONSPRINTSTOPPASSIVE",
                        "SPRINTSTOP", "ONSPRINTSTOP", "STOPSPRINT", "ONSTOPSPRINT",
                        "SPRINTOFF", "ONSPRINTOFF", "SPRINTEND", "ONSPRINTEND"),

        HEAL("PASSIVE_HEAL",
                        "HEALPASSIVE", "PASSIVEHEAL", "ONHEALPASSIVE",
                        "HEAL", "ONHEAL", "REGEN", "ONREGEN", "ONHEALING", "HEALING"),

        PORTAL("PASSIVE_PORTAL",
                        "PORTALPASSIVE", "PASSIVEPORTAL", "ONPORTALPASSIVE",
                        "PORTAL", "ONPORTAL", "PORTALENTER", "ONPORTALENTER",
                        "PORTALUSE", "ONPORTALUSE", "ONUSEPORTAL", "USEPORTAL"),

        TELEPORT("PASSIVE_TELEPORT",
                        "TELEPORTPASSIVE", "PASSIVETELEPORT", "ONTELEPORT",
                        "TP", "ONTP",
                        "TELEPORTED", "ONTELEPORTED");

        private static final Map<String, PassiveTrigger> BY_NAME = new HashMap<>();

        /**
         * Резервные имена, которые пассивка принимает, но не регистрирует
         * в MythicMobs (ими владеют MythicMobs/MythicCrucible):
         * {@code ~onTimer}, {@code ~onJump}, {@code ~onAttack}, {@code ~onUse}…
         * <p>
         * Эти имена нельзя удалять из списка алиасов enum: {@link #RESERVED_ALIASES}
         * только запрещает их регистрацию, но разбор строк обязан их понимать.
         */
        private static final List<String> RESERVED_ALIASES = List.of(
                "ONUSE", "USE", "USEITEM", "ONUSEITEM", "RIGHTCLICKAIR", "RIGHTCLICKBLOCK",
                "ONLEFTCLICK", "LEFTCLICK",
                "ONBREAK", "BREAK", "ONMINE", "MINE",
                "ONPICKUP", "PICKUP",
                "ONATTACK", "ATTACK",
                "ONDAMAGE", "DAMAGE",
                "ONJUMP", "JUMP",
                "ONBOW", "BOW",
                "ONDROP", "DROP", "DROPITEM", "ONDROPITEM",
                "ONTIMER", "TIMER", "ONTICK", "TICK",
                "ONSWAP", "SWAP", "ONSWAPHANDS", "SWAPHANDS",
                "ONCHAT", "CHAT",
                "ONEQUIP", "EQUIP", "ONUNEQUIP", "UNEQUIP"
        );

        /** Строки, которые разбираются, но в MythicMobs не регистрируются. */
        private static final Map<String, PassiveTrigger> BY_RESERVED = new HashMap<>();

        static {
                for (PassiveTrigger trigger : values()) {
                        BY_NAME.put(normalize(trigger.triggerName), trigger);
                        for (String alias : trigger.aliases) {
                                BY_NAME.put(normalize(alias), trigger);
                        }
                }

                for (String name : RESERVED_ALIASES) {
                        PassiveTrigger trigger = guessReserved(name);
                        if (trigger != null) {
                                BY_RESERVED.put(normalize(name), trigger);
                        }
                }
        }

        /**
         * Сопоставляет зарезервированное имя своему триггеру по префиксу.
         * Служит страховкой: даже если алиас случайно пропадёт из списка
         * {@code enum}, строка вроде {@code ~onJump} всё равно разберётся.
         */
        private static PassiveTrigger guessReserved(String name) {
                String n = normalize(name);

                // Сначала точные имена: префиксы вроде "USE" и "ONUSE"
                // иначе перехватили бы onUseItem/onUseStrict.
                switch (n) {
                        case "ONUSE":
                        case "USE":
                                return RIGHT_CLICK;
                        case "ONLEFTCLICK":
                        case "LEFTCLICK":
                                return LEFT_CLICK;
                        case "ONATTACK":
                        case "ATTACK":
                                return ATTACK;
                        case "ONDAMAGE":
                        case "DAMAGE":
                                return DAMAGED;
                        case "ONJUMP":
                        case "JUMP":
                                return JUMP;
                        case "ONBOW":
                        case "BOW":
                                return SHOOT;
                        case "ONBREAK":
                        case "BREAK":
                        case "ONMINE":
                        case "MINE":
                                return BLOCK_BREAK;
                        case "ONPICKUP":
                        case "PICKUP":
                                return PICKUP;
                        case "ONDROP":
                        case "DROP":
                        case "DROPITEM":
                                return PRESS_Q;
                        case "ONTIMER":
                        case "TIMER":
                        case "ONTICK":
                        case "TICK":
                                return INTERVAL;
                        case "ONSWAP":
                        case "SWAP":
                        case "ONSWAPHANDS":
                        case "SWAPHANDS":
                                return PRESS_F;
                        case "ONCHAT":
                        case "CHAT":
                                return PRESS_T;
                        case "ONEQUIP":
                        case "EQUIP":
                        return EQUIP;
                        case "ONUNEQUIP":
                        case "UNEQUIP":
                                return UNEQUIP;
                        default:
                                return null;
                }
        }



        private final String triggerName;
        private final String[] aliases;

        PassiveTrigger(String triggerName, String... aliases) {
                this.triggerName = triggerName;
                this.aliases = aliases;
        }

        public String triggerName() {
                return triggerName;
        }

        public SkillTrigger skillTrigger() {
                return SkillTrigger.get(triggerName);
        }

        public static PassiveTrigger parse(String raw) {
                if (raw == null || raw.isBlank()) {
                        return INTERVAL;
                }
                String name = normalize(raw);
                PassiveTrigger trigger = BY_NAME.get(name);
                return trigger != null ? trigger : BY_RESERVED.get(name);
        }

        /**
         * Регистрирует кастомные триггеры (и алиасы, которые не заняты MythicMobs /
         * MythicCrucible) в MythicMobs.
         * <p>
         * Вызывается, когда MythicMobs и MythicCrucible уже включены, и повторно
         * после {@code /mm reload}. Повторная регистрация безопасна: MythicMobs
         * использует {@code putIfAbsent}, поэтому уже занятые имена не меняются.
         */
        public static synchronized void registerAll() {
                for (PassiveTrigger trigger : values()) {
                        SkillTrigger.create(trigger.triggerName, trigger.registrationAliases()).register();
                }
        }

        /**
         * Алиасы, которые можно безопасно занять: собственные не заняты внешними
         * плагинами по умолчанию, а {@code ~onPress_F} MythicCrucible регистрирует сам.
         */
        private String[] registrationAliases() {
                List<String> safe = new ArrayList<>(aliases.length);
                for (String alias : aliases) {
                        if (!RESERVED_ALIASES.contains(normalize(alias))) {
                                safe.add(alias);
                        }
                }
                return safe.toArray(String[]::new);
        }



        /**
         * Нормализует имя триггера: {@code ~onTimer}, {@code on_timer}, {@code ONTIMER:20}
         * дают одинаковый ключ. Период ({@code :20}) отрезается — его разбирает фабрика.
         */
        private static String normalize(String raw) {
                String value = raw.trim();
                int colon = value.indexOf(':');
                if (colon > 0) {
                        value = value.substring(0, colon);
                }
                if (value.startsWith("~")) {
                        value = value.substring(1);
                }
                return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        }
}
