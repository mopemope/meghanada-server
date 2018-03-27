package meghanada;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class L1 {

  private static Logger log = LogManager.getLogger(L1.class);

  public void go() {
    Map<String, Long> map = new HashMap<>();
    map.forEach((key, value) -> {});
  }
}
