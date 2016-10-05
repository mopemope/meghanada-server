package meghanada;

import static meghanada.DummyUtils.checkNotNull;

public class Gen11 {

    public static CharSequence test1(CharSequence value) {
        int length = checkNotNull(value, "value").length();
        return value;
    }

    public static String test2(Class<?> clazz) {
        String name = DummyUtils.checkNotNull(clazz, "value").getName();
        return name;
    }
}
