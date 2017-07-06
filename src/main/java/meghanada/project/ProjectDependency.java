package meghanada.project;

import static java.util.Objects.nonNull;
import static meghanada.utils.FunctionUtils.wrapIO;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import meghanada.analyze.CompileResult;
import meghanada.config.Config;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProjectDependency implements Serializable {

  private static final long serialVersionUID = 6556924934040150075L;
  private static final Logger log = LogManager.getLogger(ProjectDependency.class);

  private final String id;
  private final String scope;
  private final String version;
  private final File file;
  private final Type type;

  private transient String dependencyFilePath;
  private transient Set<File> cachedSrc;

  public ProjectDependency(
      final String id, final String scope, final String version, final File file, final Type type) {
    this.id = id;
    this.scope = scope.toUpperCase();
    this.version = version;
    this.file = file;
    this.type = type;
  }

  public static Type getFileType(final File file) {
    return file.isFile() ? Type.JAR : Type.DIRECTORY;
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public String getScope() {
    return scope;
  }

  public File getFile() {
    if (type.equals(Type.PROJECT)) {
      // eval project
      return file;
    } else {
      return file;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProjectDependency that = (ProjectDependency) o;
    return Objects.equal(id, that.id)
        && Objects.equal(scope, that.scope)
        && Objects.equal(version, that.version)
        && Objects.equal(file, that.file)
        && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, scope, version, file, type);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("scope", scope)
        .add("version", version)
        .add("file", file)
        .add("type", type)
        .toString();
  }

  public String string() {
    return MoreObjects.toStringHelper(this).add("type", type).add("file", file).toString();
  }

  public Type getType() {
    return type;
  }

  public String getDependencyFilePath() {
    try {
      if (type.equals(Type.PROJECT)) {
        if (nonNull(this.dependencyFilePath)) {
          return this.dependencyFilePath;
        }

        // get gradle's project archive
        final File projectArchive =
            new File(file, "build" + File.separator + "libs" + File.separator + this.id + ".jar");

        if (projectArchive.exists()) {
          // add index
          final List<File> temp = new ArrayList<>(1);
          temp.add(projectArchive);
          final CachedASMReflector reflector = CachedASMReflector.getInstance();
          reflector.createClassIndexes(temp);
          this.dependencyFilePath = projectArchive.getCanonicalPath();
        } else {
          if (nonNull(this.dependencyFilePath)) {
            return this.dependencyFilePath;
          }

          String root = Config.getProjectRoot();
          try {
            final File output = getProjectOutput();
            this.dependencyFilePath = output.getCanonicalPath();
          } finally {
            Config.setProjectRoot(root);
          }
        }
        return this.dependencyFilePath;
      } else {
        return file.getCanonicalPath();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nonnull
  private File getProjectOutput() throws IOException {
    return Session.findProject(this.file)
        .map(
            project -> {
              // cache build result
              try {
                project.setSubProject(true);
                if (Config.load().isSkipBuildSubProjects()) {
                  log.info("skip build project {}", project.getName());
                  return project.getOutput();
                }
                CompileResult compileResult = project.compileJava();
                if (!compileResult.isSuccess()) {
                  log.warn("Compile Error : {}", compileResult.getDiagnosticsSummary());
                }
                return project.getOutput();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              } finally {
                project.setSubProject(false);
              }
            })
        .orElse(this.file);
  }

  public Set<File> getProjectSources() {
    String root = Config.getProjectRoot();
    try {
      if (type.equals(Type.PROJECT)) {
        if (nonNull(cachedSrc)) {
          return cachedSrc;
        }
        this.cachedSrc =
            Session.findProject(this.file)
                .map(wrapIO(Project::getAllSources))
                .orElse(Collections.emptySet());
        return this.cachedSrc;
      }
      return Collections.emptySet();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      Config.setProjectRoot(root);
    }
  }

  public enum Type {
    JAR,
    DIRECTORY,
    PROJECT
  }
}
