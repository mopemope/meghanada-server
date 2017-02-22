package meghanada;

import java.util.ArrayList;
import java.util.List;

public class Overload1 {

    public void over(String i) {

    }

    public void over(Integer i) {

    }

    public void over(List<? extends List> l) {

    }

    public void call() {
        ArrayList<? extends List> l = new ArrayList<>();
        over(l);

        int i = 0;
        over(i);
    }
}
