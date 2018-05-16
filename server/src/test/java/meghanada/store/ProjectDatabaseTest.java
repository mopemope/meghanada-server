package meghanada.store;

import static java.util.Objects.nonNull;
import static meghanada.GradleTestBase.TEMP_PROJECT_SETTING_DIR;
import static meghanada.config.Config.timeItF;
import static org.junit.Assert.assertEquals;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import meghanada.config.Config;
import meghanada.reflect.ClassIndex;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ProjectDatabaseTest {

  ProjectDatabase database;

  @Before
  public void setup() throws Exception {
    System.setProperty("meghanada.source.cache", "false");
    final File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
    final String path = tempDir.getCanonicalPath();
    System.setProperty(TEMP_PROJECT_SETTING_DIR, path);
    org.apache.commons.io.FileUtils.deleteDirectory(new File(path));
    String projectRoot = new File(".").getCanonicalPath();
    Config.setProjectRoot(projectRoot);

    database = ProjectDatabase.getInstance();
  }

  @After
  public void tearDown() throws InterruptedException, IOException {
    if (nonNull(database)) {
      database.shutdown();
    }
    String p = System.getProperty(TEMP_PROJECT_SETTING_DIR);
    org.apache.commons.io.FileUtils.deleteDirectory(new File(p));
  }

  @Test
  public void testStore01() throws Exception {
    String name = "java.lang.String";

    ClassIndex ci = null;
    int count = 10;
    for (int i = 0; i < count; i++) {
      ci =
          timeItF(
              "store:{} count:" + i,
              () -> {
                ClassIndex c =
                    new ClassIndex(name, Collections.emptyList(), Collections.emptyList());
                long l = database.storeObject(c);
                return c;
              });
    }

    for (int i = 0; i < count; i++) {
      ClassIndex ci2 =
          timeItF(
              "load:{} count:" + i,
              () -> database.loadObject(ClassIndex.ENTITY_TYPE, name, ClassIndex.class));
      assertEquals(ci, ci2);
    }
    long size = database.size(ClassIndex.ENTITY_TYPE);
    assertEquals(1, size);
  }

  @Test
  public void testStore02() throws Exception {
    String name = "java.lang.String";

    ClassIndex ci = null;
    int count = 100;
    for (int i = 0; i < count; i++) {
      ci =
          timeItF(
              "store:{} count:" + i,
              () -> {
                ClassIndex c =
                    new ClassIndex(name, Collections.emptyList(), Collections.emptyList());
                long l = database.storeObject(c, false);
                return c;
              });
    }

    for (int i = 0; i < count; i++) {
      ClassIndex ci2 =
          timeItF(
              "load:{} count:" + i,
              () -> database.loadObject(ClassIndex.ENTITY_TYPE, name, ClassIndex.class));
      assertEquals(ci, ci2);
    }
    long size = database.size(ClassIndex.ENTITY_TYPE);
    assertEquals(1, size);
  }

  @Test
  public void testStore03() throws Exception {
    String name = "java.lang.String";

    ClassIndex ci = null;
    int count = 100;
    List<ClassIndex> lst = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ClassIndex c = new ClassIndex(name + i, Collections.emptyList(), Collections.emptyList());
      lst.add(c);
    }
    long l = database.storeObjects(lst, true);
    lst.forEach(
        c -> {
          System.out.println(c.getEntityId());
        });
  }
}
