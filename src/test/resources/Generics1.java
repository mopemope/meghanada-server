import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;

public class Generics1 {

    public void testGenerics() {
        final List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = Joiner.on(", ").appendTo(sb, list);
    }

    private int getInt() {
        return 1;
    }

    public Long testGenerics() {
        final List<Map<String, Long>> list = new ArrayList<>();
        return list.get(0).get("");
    }

}
