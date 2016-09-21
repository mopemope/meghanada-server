package meghanada.project.maven;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import meghanada.project.ProjectSerializer;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@DefaultSerializer(ProjectSerializer.class)
public class MavenProject extends Project {

    private static Logger log = LogManager.getLogger(MavenProject.class);
    private File pomFile;
    private String maven = "mvn";

    public MavenProject(File projectRoot) throws IOException {
        super(projectRoot);
        this.pomFile = new File(projectRoot, "pom.xml");
    }

    public void setMavenPath(String maven) {
        this.maven = maven;
    }

    private String getVersion(final String path) {
        return new File(path).getName();
    }

    private String getArtifactCode(final String path) {
        final File parentFile = new File(path).getParentFile();
        final String artifactID = parentFile.getName();

        String parent = parentFile.getParent();
        final int i = parent.indexOf("repository");
        if (i > 0) {
            parent = parent.substring(i + 10);
        }
        final String groupID = ClassNameUtils.replace(parent, File.separator, ".");
        return groupID + ":" + artifactID;
    }

    @Override
    public Project parseProject() throws ProjectParseException {
        try {
            final String mavenPath = Config.load().getMavenPath();
            if (!Strings.isNullOrEmpty(mavenPath)) {
                this.setMavenPath(mavenPath);
            }

            final File logFile = File.createTempFile("meghanada-cp", ".log");
            logFile.deleteOnExit();
            final String logPath = logFile.getCanonicalPath();
            this.runMvn("dependency:build-classpath", String.format("-Dmdep.outputFile=%s", logPath));
            final String cpTxt = Files.readFirstLine(logFile, Charset.forName("UTF-8"));
            final String[] depends = cpTxt.split(File.pathSeparator);

            for (final String dep : depends) {
                final File file = new File(dep);
                final String parentPath = file.getParent();
                final String version = this.getVersion(parentPath);
                final String code = this.getArtifactCode(parentPath);
                final ProjectDependency dependency = new ProjectDependency(code, "COMPILE", version, file);
                super.dependencies.add(dependency);
            }

            final POMParser pomParser = new POMParser(this.projectRoot);
            final POMInfo pomInfo = pomParser.parsePom(this.pomFile);

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
            super.compileSource = pomInfo.compileSource;
            super.compileTarget = pomInfo.compileTarget;
        } catch (IOException | InterruptedException e) {
            throw new ProjectParseException(e);
        }
        return this;
    }

    @Override
    public InputStream runTask(List<String> args) throws IOException {
        List<String> mvnCmd = new ArrayList<>();
        mvnCmd.add(this.maven);
        mvnCmd.addAll(args);
        return super.runProcess(mvnCmd);
    }

    private void runMvn(final String... args) throws IOException, InterruptedException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(this.maven);
        for (String arg : args) {
            cmd.add(arg);
        }

        final ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.directory(this.projectRoot);
        final String cmdString = String.join(" ", cmd);

        log.debug("RUN cmd: {}", cmdString);

        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();
        try (InputStream in = process.getInputStream()) {
            byte[] buf = new byte[8192];
            while (in.read(buf) != -1) {
            }
        }
        process.waitFor();
    }


}
