package meghanada;

import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.BeforeSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
import org.junit.extensions.cpsuite.ClasspathSuite.IncludeJars;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("CheckReturnValue")
@RunWith(ClasspathSuite.class)
@ClassnameFilters({".*Test"})
@IncludeJars(true)
public class AllTests {

    @BeforeSuite
    public static void init() throws Exception {
        final Project project = new GradleProject(new File("./").getCanonicalFile());
        project.parseProject();
        project.clearCache();
        final List<File> jars = project.getDependencies().stream()
                .map(ProjectDependency::getFile)
                .collect(Collectors.toList());
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        reflector.addClasspath(jars);
        reflector.createClassIndexes();
        project.compileJava(false);
        project.compileTestJava(false);
        reflector.updateClassIndexFromDirectory();
    }
}
