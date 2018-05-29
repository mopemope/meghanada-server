package meghanada.project.eclipse;

import static java.util.Objects.nonNull;

import com.google.common.base.Splitter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.ProjectParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EclipseProject extends Project {

  private static final Logger log = LogManager.getLogger(EclipseProject.class);
  private static final long serialVersionUID = -7812031255506012481L;
  private File projectFile;
  private File classPathFile;

  public EclipseProject(File projectRoot) throws IOException {
    super(projectRoot);
    this.projectFile = new File(projectRoot, ".project");
    this.classPathFile = new File(projectRoot, ".classpath");
  }

  @Override
  public Project parseProject(File projectRoot, File current) throws ProjectParseException {
    parseClassPathFile(this.classPathFile);
    return this;
  }

  @Override
  public InputStream runTask(List<String> args) throws IOException {
    throw new UnsupportedOperationException("Not support");
  }

  @Override
  public String getProjectType() {
    return "eclipse";
  }

  private void parseClassPathFile(File f) throws ProjectParseException {
    final XMLInputFactory factory = XMLInputFactory.newInstance();
    try (final InputStream in = new FileInputStream(f)) {
      final XMLStreamReader reader = factory.createXMLStreamReader(in);
      while (reader.hasNext()) {
        if (reader.isStartElement() && reader.getLocalName().equals("classpathentry")) {

          String kind = reader.getAttributeValue("", "kind");
          String output = reader.getAttributeValue("", "output");
          String path = reader.getAttributeValue("", "path");
          List<String> paths = Splitter.on(File.separator).splitToList(path);
          Set<String> set = new HashSet<>(paths);

          if (kind.equals("src")) {
            if (path.contains(File.separator + "resources")) {
              if (paths.contains("test")) {
                this.testResources.add(new File(this.projectRoot, path));
                if (nonNull(output)) {
                  this.testOutput = new File(this.projectRoot, output);
                }
              } else {
                this.resources.add(new File(this.projectRoot, path));
                if (nonNull(output)) {
                  this.output = new File(this.projectRoot, output);
                }
              }
            } else {
              if (paths.contains("test")) {
                this.testSources.add(new File(this.projectRoot, path));
                if (nonNull(output)) {
                  this.testOutput = new File(this.projectRoot, output);
                }
              } else {
                this.sources.add(new File(this.projectRoot, path));
                if (nonNull(output)) {
                  this.output = new File(this.projectRoot, output);
                }
              }
            }
          } else if (kind.equals("lib")) {

            File file = new File(path);
            String code = this.getArtifactCode(path);
            String version = this.getArtifactVersion(path);
            ProjectDependency.Type type = ProjectDependency.getFileType(file);
            ProjectDependency dependency =
                new ProjectDependency(code, "COMPILE", version, file, type);
            this.dependencies.add(dependency);
          } else if (kind.equals("output")) {
            this.output = new File(this.projectRoot, path);
            this.testOutput = new File(this.projectRoot, path);
          }
        }
        reader.next();
      }
      reader.close();
    } catch (IOException | XMLStreamException e) {
      throw new ProjectParseException(e);
    }
  }

  private String getArtifactVersion(final String path) {
    File file = new File(path);
    String name = file.getName();
    int i = name.lastIndexOf("-");
    if (i > -1) {
      int length = name.length();
      return name.substring(i + 1, length - 4);
    }
    return "1.0.0";
  }

  private String getArtifactCode(final String path) {
    // ivy or gradle cache
    File file = new File(path);
    File verFile = file.getParentFile().getParentFile();
    String version = verFile.getName();
    File artifactFile = verFile.getParentFile();
    String artifactID = artifactFile.getName();
    File groupFile = artifactFile.getParentFile();
    String groupID = groupFile.getName();
    return groupID + ":" + artifactID + ":" + getArtifactVersion(path);
  }
}
