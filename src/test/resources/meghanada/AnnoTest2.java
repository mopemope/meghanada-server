package meghanada;

import meghanada.project.Project;
import meghanada.project.gradle.GradleProject;
// import org.junit.extensions.cpsuite.ClasspathSuite;
// import org.junit.extensions.cpsuite.ClasspathSuite.BeforeSuite;
// import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
// import org.junit.extensions.cpsuite.ClasspathSuite.IncludeJars;
// import org.junit.runner.RunWith;


import java.io.File;

@RunWith(ClasspathSuite.class)
@ClassnameFilters({".*Test"})
@IncludeJars(true)
public class AnnoTest2 {

    @BeforeSuite
    public static void init() throws Exception {
        final Project project = new GradleProject(new File("./").getCanonicalFile());
        project.parseProject();
        project.compileJava(false);
        project.compileTestJava(false);
    }
}
