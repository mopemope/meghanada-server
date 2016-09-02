package meghanada;

import java.util.ArrayList;
import java.util.List;

public class CallGen5 {

    public void test1() {
        Gen5 gen5 = new Gen5();
        final String name = gen5.name;
        gen5.getFormalClass("", ArrayList.class).size();
        final List<String> list = gen5.getList();
        int size = list.size();
    }

}
