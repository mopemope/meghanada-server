package meghanada.project;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import meghanada.project.gradle.GradleProject;
import meghanada.project.maven.MavenProject;
import meghanada.project.meghanada.MeghanadaProject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProjectSerializer extends Serializer<Project> {

  private static final Logger log = LogManager.getLogger(Project.class);
  private static final int TYPE_GRADLE = 1;
  private static final int TYPE_MAVEN = 2;
  private static final int TYPE_MEGHANADA = 3;

  @Override
  public void write(Kryo kryo, Output output, Project project) {
    try {
      if (project instanceof GradleProject) {
        output.writeInt(TYPE_GRADLE, true);
      } else if (project instanceof MavenProject) {
        output.writeInt(TYPE_MAVEN, true);
      } else if (project instanceof MeghanadaProject) {
        output.writeInt(TYPE_MEGHANADA, true);
      }

      // protected File projectRoot;
      final String projectRoot = project.getProjectRoot().getCanonicalPath();
      output.writeString(projectRoot);

      // protected Set<ProjectDependency> dependencies = new HashSet<>();
      final Set<ProjectDependency> dependencies = project.dependencies;
      output.writeInt(dependencies.size(), true);
      for (final ProjectDependency projectDependency : dependencies) {
        kryo.writeClassAndObject(output, projectDependency);
      }

      // protected Set<File> sources = new HashSet<>();
      final Set<File> sources = project.sources;
      output.writeInt(sources.size(), true);
      for (final File f : sources) {
        output.writeString(f.getCanonicalPath());
      }

      // protected Set<File> resources = new HashSet<>();
      final Set<File> resources = project.resources;
      output.writeInt(resources.size(), true);
      for (final File f : resources) {
        output.writeString(f.getCanonicalPath());
      }

      // protected File output;
      final File out = project.output;
      if (out != null) {
        output.writeString(out.getCanonicalPath());
      } else {
        output.writeString(null);
      }

      // protected Set<File> testSources = new HashSet<>();
      final Set<File> testSources = project.testSources;
      output.writeInt(testSources.size(), true);
      for (final File f : testSources) {
        output.writeString(f.getCanonicalPath());
      }

      // protected Set<File> testResources = new HashSet<>();
      final Set<File> testResources = project.testResources;
      output.writeInt(testResources.size(), true);
      for (final File f : testResources) {
        output.writeString(f.getCanonicalPath());
      }

      // protected File testOutput;
      final File testOutput = project.testOutput;
      if (testOutput != null) {
        output.writeString(testOutput.getCanonicalPath());
      } else {
        output.writeString(null);
      }

      // protected String compileSource = "1.8";
      output.writeString(project.compileSource);

      // protected String compileTarget = "1.8";
      output.writeString(project.compileTarget);

      // protected String id;
      output.writeString(project.id);

      // protected boolean isAndroidProject;
      output.writeBoolean(project.isAndroidProject);
      // protected String name;
      output.writeString(project.name);

    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Override
  public Project read(Kryo kryo, Input input, Class<Project> type) {
    try {
      final int typeInt = input.readInt(true);
      // protected File projectRoot;
      final String projectRoot = input.readString().trim();

      Project project;
      if (typeInt == TYPE_GRADLE) {
        project = new GradleProject(new File(projectRoot));
      } else if (typeInt == TYPE_MAVEN) {
        project = new MavenProject(new File(projectRoot));
      } else {
        project = new MavenProject(new File(projectRoot));
      }

      // protected Set<ProjectDependency> dependencies = new HashSet<>();
      {
        final int size = input.readInt(true);
        for (int i = 0; i < size; i++) {
          final ProjectDependency dependency = (ProjectDependency) kryo.readClassAndObject(input);
          project.dependencies.add(dependency);
        }
      }

      // protected Set<File> sources = new HashSet<>();
      {
        final int size = input.readInt(true);
        for (int i = 0; i < size; i++) {
          final String path = input.readString();
          project.sources.add(new File(path.trim()));
        }
      }

      // protected Set<File> resources = new HashSet<>();
      {
        final int size = input.readInt(true);
        for (int i = 0; i < size; i++) {
          final String path = input.readString();
          project.resources.add(new File(path.trim()));
        }
      }

      // protected File output;
      {
        final String path = input.readString();
        if (path != null) {
          project.output = new File(path.trim());
        }
      }

      // protected Set<File> testSources = new HashSet<>();
      {
        final int size = input.readInt(true);
        for (int i = 0; i < size; i++) {
          final String path = input.readString();
          project.testSources.add(new File(path.trim()));
        }
      }

      // protected Set<File> testResources = new HashSet<>();
      {
        final int size = input.readInt(true);
        for (int i = 0; i < size; i++) {
          final String path = input.readString();
          project.testResources.add(new File(path.trim()));
        }
      }

      // protected File testOutput;
      {
        final String path = input.readString();
        if (path != null) {
          project.testOutput = new File(path.trim());
        }
      }

      // protected String compileSource = "1.8";
      {
        final String s = input.readString();
        if (s != null) {
          project.compileSource = s.trim();
        }
      }
      // protected String compileTarget = "1.8";
      {
        final String s = input.readString();
        if (s != null) {
          project.compileTarget = s.trim();
        }
      }

      // protected String ID;
      {
        final String s = input.readString();
        if (s != null) {
          project.id = s.trim();
        }
      }

      project.isAndroidProject = input.readBoolean();
      project.name = input.readString().trim();

      project.loadCaller();
      return project;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
