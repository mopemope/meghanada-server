package meghanada;

import java.util.stream.IntStream;

public class ArrayOverload {

  public static void over(int[] args1, String[] arg2) {}

  public static void over(String arg2) {}

  public void call() {
    ArrayOverload.over(IntStream.range(0, 10).toArray(), new String[] {"TEST"});
  }
}
