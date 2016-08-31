import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Generics2 {

    public void testGenerics1() {
        List<Gen2.Entry<String>> lst = new ArrayList<>();
        for(Gen2.Entry<String> entry : lst) {
            entry.value.startsWith("S");
        }
    }


}
