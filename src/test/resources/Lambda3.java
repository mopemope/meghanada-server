import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Lambda3 {

    public void testWithMethodRef1() {
        final List<String> list = new ArrayList<>();
        final List<Integer> integerList = list.stream()
                .map(Integer::parseInt)
                .filter(integer -> integer > 0)
                .collect(Collectors.toList());
    }

    public void testWithMethodRef2() {
        final List<String> list = new ArrayList<>();
        final List<Integer> integerList = list.stream()
                .filter(String::isEmpty)
                .map(s -> {
                    return Integer.parseInt(s);
                })
                .collect(Collectors.toList());
    }

}
