import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Lambda1 {

    public void testBlockLambda() {
        final List<String> list = new ArrayList<>();
        final List<Integer> integerList = list.stream()
                .filter(s -> {
                    return s != null;
                })
                .map(s -> {
                    final int i = Integer.parseInt(s);
                    return i;
                })
                .collect(Collectors.toList());
    }
}
