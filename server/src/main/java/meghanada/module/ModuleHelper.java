package meghanada.module;

import static java.util.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import meghanada.telemetry.TelemetryUtils;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModuleHelper {

  private static final Logger log = LogManager.getLogger(ModuleHelper.class);

  private ModuleHelper() {}

  public static File getJrtFsFile() {
    return new File("jrt-fs.jar");
  }

  public static boolean isJrtFsFile(File f) {
    return f.getPath().equals("jrt-fs.jar");
  }

  public static void walkModule(ModuleConsumer c) throws IOException {
    FileSystem fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path dir = fileSystem.getPath(File.separator + "modules");
    walkModuleFS(dir, c);
  }

  public static <T> Optional<T> searchModule(ModuleSupplier<T> ms) throws IOException {
    FileSystem fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path dir = fileSystem.getPath(File.separator + "modules");
    return searchModuleFS(dir, ms);
  }

  public static void walkModule(String mod, ModuleConsumer c) throws IOException {
    FileSystem fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path dir = fileSystem.getPath(File.separator + "modules" + File.separator + mod);
    walkModuleFS(dir, c);
  }

  public static InputStream getInputStream(String path) throws IOException {
    FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path fsPath = fs.getPath(path);
    return Files.newInputStream(fsPath);
  }

  private static void walkModuleFS(Path path, ModuleConsumer consumer) throws IOException {
    boolean directory = Files.isDirectory(path);
    if (directory) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
        for (Path p : stream) {
          walkModuleFS(p, consumer);
        }
      }
    } else {
      consumer.accept(path);
    }
  }

  private static <T> Optional<T> searchModuleFS(Path path, ModuleSupplier<T> ms)
      throws IOException {
    boolean directory = Files.isDirectory(path);
    if (directory) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
        for (Path p : stream) {
          Optional<T> o = searchModuleFS(p, ms);
          if (o.isPresent()) {
            return o;
          }
        }
      }
    } else {
      T t = ms.get(path);
      if (nonNull(t)) {
        return Optional.of(t);
      }
    }
    return Optional.empty();
  }

  public static Optional<String> pathToClassName(Path p) {

    if (p.startsWith(File.separator + "modules" + File.separator)) {
      // module path
      Path subpath = p.subpath(2, p.getNameCount());

      String s = ClassNameUtils.replaceSlash(subpath.toString());
      if (s.endsWith(".class")) {
        s = s.substring(0, s.length() - 6);
        return Optional.of(s);
      }
    }
    return Optional.empty();
  }

  public static Optional<String> pathToModule(Path p) {

    if (p.startsWith(File.separator + "modules" + File.separator)) {
      // module path
      Path subpath = p.subpath(1, 2);
      return Optional.of(subpath.toString());
    }
    return Optional.empty();
  }

  @SuppressWarnings("try")
  public static Optional<ClassData> pathToClassData(Path p) {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("ModuleHelper.pathToClassData")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", p.toString()).build("args"));

      if (p.startsWith(File.separator + "modules" + File.separator)) {
        Path sub = p.subpath(2, p.getNameCount());
        String s = ClassNameUtils.replaceSlash(sub.toString());
        if (s.endsWith(".class")) {
          String className = s.substring(0, s.length() - 6);
          Path mod = p.subpath(1, 2);
          String module = mod.toString();
          ClassData cd = new ClassData(module, className, p);
          return Optional.of(cd);
        }
      }
      return Optional.empty();
    }
  }

  @FunctionalInterface
  public interface ModuleConsumer {
    void accept(Path path) throws IOException;
  }

  @FunctionalInterface
  public interface ModuleSupplier<T> {
    T get(Path path) throws IOException;
  }

  public static final class ClassData {

    private final String moduleName;
    private final String className;
    private final Path path;

    ClassData(String moduleName, String className, Path path) {
      this.moduleName = moduleName;
      this.className = className;
      this.path = path;
    }

    public String getModuleName() {
      return moduleName;
    }

    public String getClassName() {
      return className;
    }

    public InputStream getInputStream() throws IOException {
      FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
      return Files.newInputStream(this.path);
    }
  }
}
