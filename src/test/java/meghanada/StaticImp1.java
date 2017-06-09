package meghanada;

import static org.junit.Assert.assertTrue;

public class StaticImp1 {

  public void testExists() {
    try {

      Thread.currentThread().interrupt();
      assertTrue("Assert", Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
