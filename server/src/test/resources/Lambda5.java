import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Lambda5 {

  public void test1() {
    List<String> list = new ArrayList<>();
    final Optional<String> first =
        list.stream()
            .map(s -> new ArrayList<String>())
            .flatMap(
                a -> {
                  return a.stream();
                })
            .findFirst();
  }
}
