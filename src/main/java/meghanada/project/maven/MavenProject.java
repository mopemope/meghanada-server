package meghanada.project.maven;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.annotations.Beta;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Beta
@DefaultSerializer(ProjectSerializer.class)
public class MavenProject extends Project {

    private static Logger log = LogManager.getLogger(MavenProject.class);
    private File pomFile;

    public MavenProject(File projectRoot) throws IOException {
        super(projectRoot);
        this.pomFile = new File(projectRoot, "pom.xml");
    }

    @Override
    public Project parseProject() throws ProjectParseException {
        POMParser pomParser = new POMParser(this.projectRoot);
        POMInfo pomInfo = pomParser.parsePom(this.pomFile);

        {
            super.sources.addAll(pomInfo.getSourceDirectory());
            File src = new File(this.projectRoot, String.join(File.separator, "src", "main", "java"));
            super.sources.add(src);

            super.resources.addAll(pomInfo.getResourceDirectories());
            File resource = new File(this.projectRoot, String.join(File.separator, "src", "main", "resource"));
            super.resources.add(resource);

            File output = pomInfo.getOutputDirectory();
            if (output == null) {
                output = new File(this.projectRoot, String.join(File.separator, "target", "classes"));
            }
            super.output = output;
        }

        {
            super.testSources.addAll(pomInfo.getTestSourceDirectory());
            File src = new File(this.projectRoot, String.join(File.separator, "src", "test", "java"));
            super.testSources.add(src);

            super.testResources.addAll(pomInfo.getTestResourceDirectories());
            File testResource = new File(this.projectRoot, String.join(File.separator, "src", "test", "resource"));
            super.testResources.add(testResource);

            File output = pomInfo.getTestOutputDirectory();
            if (output == null) {
                output = new File(this.projectRoot, String.join(File.separator, "target", "test-classes"));
            }
            super.testOutput = output;
        }

        this.dependencies.addAll(pomInfo.getDependencyHashMap().values());

        log.debug("sources {}", this.sources);
        log.debug("resources {}", this.resources);
        log.debug("output {}", this.output);

        log.debug("test sources {}", this.testSources);
        log.debug("test resources {}", this.testResources);
        log.debug("test output {}", this.testOutput);

        for (ProjectDependency projectDependency : this.getDependencies()) {
            log.debug("dependency {}", projectDependency.getId());
        }
        super.mergeFromProjectConfig();
        return this;
    }

    @Override
    public InputStream runTask(List<String> args) throws IOException {
        List<String> mvnCmd = new ArrayList<>();
        mvnCmd.add("mvn");
        mvnCmd.addAll(args);
        return super.runProcess(mvnCmd);
    }

}
