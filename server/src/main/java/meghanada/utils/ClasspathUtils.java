package meghanada.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClasspathUtils {

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
    }
  }
}
