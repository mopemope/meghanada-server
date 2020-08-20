package meghanada.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClassPathUtils {

  private static final Logger log = LogManager.getLogger(ClassPathUtils.class);

  public static void addToolsJar() throws Exception {
    String home = System.getProperty("java.home");
    String parent = new File(home).getParent();
    Path path = Paths.get(parent, "lib", "tools.jar");
    path = path.normalize();
    File file = path.toFile();
    if (file.exists()) {
      Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      method.setAccessible(true);
      method.invoke(ClassLoader.getSystemClassLoader(), file.toURI().toURL());
      // log.info("add tools.jar path:{}", path.toFile().getCanonicalPath());
    } else {
      if (isJava8()) {
        log.error("missing tools.jar path:{}", path.toFile().getCanonicalPath());
      }
    }
  }

  private static boolean isJava8() {
    String s = System.getProperty("java.specification.version");
    return s.equals("1.8");
  }
}
