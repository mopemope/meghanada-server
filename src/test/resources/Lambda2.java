import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Lambda2 {

    public void testExpressionLambda() {
        final List<String> list = new ArrayList<>();
        final List<Integer> integerList = list.stream()
                .map(s -> Integer.parseInt(s))
                .filter(integer -> integer > 0)
                .collect(Collectors.toList());
    }
}
