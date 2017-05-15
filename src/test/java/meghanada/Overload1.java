package meghanada;

import java.util.ArrayList;
import java.util.List;

public class Overload1 {

    public void over(String i) {

    }

    public void over(Integer i) {

    }

    public void over(List<? extends List<String>> l) {

    }

    public void over(String a, String... b) {

    }

    public void call() {
        ArrayList<? extends List<String>> l = new ArrayList<>();
        over(l);

        int i = 0;
        over(i);

        over("A", "B", "C", "D");
    }
}
