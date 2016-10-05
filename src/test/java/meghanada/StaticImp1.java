package meghanada;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

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

