package meghanada;

import java.io.File;
import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.BeforeSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
import org.junit.extensions.cpsuite.ClasspathSuite.IncludeJars;
import org.junit.runner.RunWith;

@RunWith(ClasspathSuite.class)
@ClassnameFilters({".*Test"})
@IncludeJars(true)
public class AnnoTest1 {

  @BeforeSuite
  public static void init() throws Exception {
    final Project project = new GradleProject(new File("./").getCanonicalFile());
    project.parseProject();
    project.compileJava(false);
    project.compileTestJava(false);
  }
}
