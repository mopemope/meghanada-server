package meghanada;

import java.util.Comparator;

public class Jump1 {

  public void test() {
    final Comparator<String> comparator = String.CASE_INSENSITIVE_ORDER;
    comparator.thenComparing(null, null);
  }
}
