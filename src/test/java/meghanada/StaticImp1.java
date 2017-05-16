package meghanada;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StaticImp1 {

  @Test
  public void testExists() {
    try {

      Thread.currentThread().interrupt();
      assertTrue("Assert", Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
