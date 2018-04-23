package meghanada.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ModuleHelperTest {

  private static final Logger log = LogManager.getLogger(ModuleHelperTest.class);

  @Test
  public void getFile() {
    File f = ModuleHelper.getJrtFsFile();
    String name = f.getPath();
    log.info(name);
    boolean jrtFsFile = ModuleHelper.isJrtFsFile(f);
    log.info(jrtFsFile);
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
                              log.info(m);
                            });
                    log.info(s);
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
                              log.info(m);
                            });
                    log.info(s);
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
                                log.info(mod + ":" + className);
                              } catch (IOException e) {
                                log.catching(e);
                              }
                            });
                  });
        });
  }
}
