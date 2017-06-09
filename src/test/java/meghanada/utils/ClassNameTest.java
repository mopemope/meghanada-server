package meghanada.utils;

import static org.junit.Assert.assertEquals;

import meghanada.GradleTestBase;
import org.junit.Test;

public class ClassNameTest extends GradleTestBase {

  @Test
  public void getName1() throws Exception {
    ClassName className = new ClassName("Map.Entry<String, Long>");
    String name = className.getName();
    assertEquals("Map.Entry", name);
  }

  @Test
  public void getName2() throws Exception {
    ClassName className = new ClassName("Map.Entry<String, Long>[]");
    String name = className.getName();
    assertEquals("Map.Entry", name);
  }

  @Test
  public void getName3() throws Exception {
    ClassName className = new ClassName("Map<K, V>.Entry");
    String name = className.getName();
    assertEquals("Map.Entry", name);
  }

  @Test
  public void getName4() throws Exception {
    ClassName className = new ClassName("ThreadLocal<int[]>");
    String name = className.getName();
    assertEquals("ThreadLocal", name);
  }

  @Test
  public void getName5() throws Exception {
    ClassName className = new ClassName("Map<Object[], Void>");
    String name = className.getName();
    assertEquals("Map", name);
  }

  @Test
  public void getName6() throws Exception {
    ClassName className = new ClassName("Map<>");
    String name = className.getName();
    assertEquals("Map", name);
  }

  @Test
  public void getName7() throws Exception {
    ClassName className = new ClassName("Map<K, V>.");
    String name = className.getName();
    assertEquals("Map.", name);
  }
}
