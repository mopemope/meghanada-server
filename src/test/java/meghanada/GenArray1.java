package meghanada;

import java.util.Map;

public class GenArray1 {

    public boolean test1(Map<Object[], Void> map, Object[] key) {
        return map.containsKey(key);
    }

    public boolean test2(ThreadLocal<int[]> local) {
        final int[] ints = new int[10];
        local.set(ints);
        return true;
    }

}
