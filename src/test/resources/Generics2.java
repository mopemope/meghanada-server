import java.util.List;

public class Generics2 {

  public void testGenerics1() {
    List<Gen2.Entry<String>> lst = new ArrayList<>();
    for (Gen2.Entry<String> entry : lst) {
      entry.value.startsWith("S");
    }
  }
}
