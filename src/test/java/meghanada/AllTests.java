package meghanada;

import meghanada.project.Project;
import meghanada.project.gradle.GradleProject;
import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
import org.junit.extensions.cpsuite.ClasspathSuite.IncludeJars;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.extensions.cpsuite.ClasspathSuite.BeforeSuite;

@RunWith(ClasspathSuite.class)
@ClassnameFilters({".*Test"})
@IncludeJars(true)
public class AllTests {

    @BeforeSuite
    public static void init() throws Exception {
        final Project project = new GradleProject(new File("./").getCanonicalFile());
        project.parseProject();
        project.compileJava(false);
        project.compileTestJava(false);
    }
}
