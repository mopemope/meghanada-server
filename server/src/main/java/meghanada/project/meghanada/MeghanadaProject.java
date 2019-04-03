package meghanada.project.meghanada;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import meghanada.project.Project;
import meghanada.project.ProjectParseException;

public class MeghanadaProject extends Project {

  private static final long serialVersionUID = -569328542609074714L;

  public MeghanadaProject(File projectRoot) throws IOException {
    super(projectRoot);
  }

  @Override
  public Project parseProject(File projectRoot, File current) throws ProjectParseException {
    return this;
  }

  @Override
  public InputStream runTask(List<String> args) throws IOException {
    throw new UnsupportedOperationException("Not support");
  }

  @Override
  public String getProjectType() {
    return "meghanada";
  }

  private Set<File> sources() {
    return this.sources;
  }

  private Set<File> resources() {
    return this.resources;
  }

  private Set<File> testSources() {
    return this.testSources;
  }

  private Set<File> testResources() {
    return this.testResources;
  }

  public static class Builder {
    private MeghanadaProject project;

    public Builder(File root) throws IOException {
      this.project = new MeghanadaProject(root);
    }

    public Builder source(File file) {
      this.project.sources().add(file);
      return this;
    }

    public Builder resource(File file) {
      this.project.resources().add(file);
      return this;
    }

    public Builder testSource(File file) {
      this.project.testSources().add(file);
      return this;
    }

    public Builder testResource(File file) {
      this.project.testResources().add(file);
      return this;
    }

    public Builder output(File file) {
      if (!file.exists()) {
        file.mkdir();
      }
      this.project.setOutput(file);
      return this;
    }

    public Builder testOutput(File file) {
      if (!file.exists()) {
        file.mkdir();
      }
      this.project.setTestOutput(file);
      return this;
    }

    public Project build() {
      return this.project;
    }
  }
}
