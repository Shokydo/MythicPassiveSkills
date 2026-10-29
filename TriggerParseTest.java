import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

public class TriggerParseTest {
    public static void main(String[] args) {
        String[] names = {"onTimer", "onTimer:1", "onJump", "onPressF", "onSneak",
                "onLeftClick", "onAttack", "onUse", "onSwing", "onBow",
                "onStartPassive", "onEndPassive", "onstartpassive", "onendpassive"};
        boolean ok = true;
        for (String name : names) {
            PassiveTrigger exact = PassiveTrigger.parseExact(name);
            System.out.printf("%-16s parseExact=%-18s lifecycle=%s%n",
                    name, exact, exact != null && exact.isLifecycle());
            if (exact == null) {
                ok = false;
            }
        }
        System.out.println(ok ? "ALL_OK" : "FAILURES");
    }
}
