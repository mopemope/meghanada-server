package meghanada.module;

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
    Path dir = fileSystem.getPath("/modules");
    walkModuleFS(dir, c);
  }

  public static void walkModule(String mod, ModuleConsumer c) throws IOException {
    FileSystem fileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
    Path dir = fileSystem.getPath("/modules/" + mod);
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

  public static Optional<String> pathToClassName(Path p) {

    if (p.startsWith("/modules/")) {
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

    if (p.startsWith("/modules/")) {
      // module path
      Path subpath = p.subpath(1, 2);
      return Optional.of(subpath.toString());
    }
    return Optional.empty();
  }

  public static Optional<ClassData> pathToClassData(Path p) {

    if (p.startsWith("/modules/")) {
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

  @FunctionalInterface
  public interface ModuleConsumer {
    void accept(Path path) throws IOException;
  }

  public static final class ClassData {

    private String moduleName;
    private String className;
    private Path path;

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
