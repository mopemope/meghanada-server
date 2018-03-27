package meghanada.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ModuleHelperTest {

  @Test
  public void getFile() {
    File f = ModuleHelper.getJrtFsFile();
    String name = f.getPath();
    System.out.println(name);
    boolean jrtFsFile = ModuleHelper.isJrtFsFile(f);
    System.out.println(jrtFsFile);
  }

  @Test
  public void walkModule1() throws IOException {
    ModuleHelper.walkModule(
        p -> {
          ModuleHelper.pathToClassName(p)
              .ifPresent(
                  s -> {
                    ModuleHelper.pathToModule(p)
                        .ifPresent(
                            m -> {
                              System.out.println(m);
                            });
                    System.out.println(s);
                  });
        });
  }

  @Test
  public void walkModule2() throws IOException {
    ModuleHelper.walkModule(
        "java.base",
        p -> {
          ModuleHelper.pathToClassName(p)
              .ifPresent(
                  s -> {
                    ModuleHelper.pathToModule(p)
                        .ifPresent(
                            m -> {
                              System.out.println(m);
                            });
                    System.out.println(s);
                  });
        });
  }

  @Test
  public void moduleToClass() throws IOException {
    Optional<String> s =
        ModuleHelper.pathToClassName(Paths.get("/modules/java.base/a/b/c/d/e/F.class"));
    Assert.assertEquals("a.b.c.d.e.F", s.get());
  }

  @Test
  public void getInputstream() throws IOException {
    ModuleHelper.walkModule(
        p -> {
          ModuleHelper.pathToClassName(p)
              .ifPresent(
                  className -> {
                    ModuleHelper.pathToModule(p)
                        .ifPresent(
                            mod -> {
                              try (InputStream in = ModuleHelper.getInputStream(p.toString())) {
                                in.available();
                                System.out.println(mod + ":" + className);
                              } catch (IOException e) {
                                e.printStackTrace();
                              }
                            });
                  });
        });
  }
}
