package meghanada.utils;

import static java.util.Objects.isNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.formatter.JavaFormatter;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.telemetry.ErrorReporter;
import meghanada.telemetry.TelemetryUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FileUtils {

  public static final String JAVA_EXT = ".java";
  public static final String JAR_EXT = ".jar";
  public static final String CLASS_EXT = ".class";
  private static final String PACKAGE_INFO_JAVA = "package-info.java";
  private static final Logger log = LogManager.getLogger(FileUtils.class);
  private static final String ALGORITHM_SHA_512 = "SHA-512";

  public static boolean isJavaFile(final File file) {
    return file.isFile() && file.getName().endsWith(JAVA_EXT) && file.exists();
  }

  @SuppressWarnings("try")
  public static String getChecksum(final File file) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpanLow("FileUtils.getChecksum")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("file", file.getPath()).build("args"));

      if (!file.exists()) {
        return RandomStringUtils.random(10);
      }
      try {

        final MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA_512);
        try (final InputStream is = Files.newInputStream(file.toPath());
            DigestInputStream dis = new DigestInputStream(is, md)) {

          final byte[] buf = new byte[4096];
          while (dis.read(buf) != -1) {}
          final byte[] digest = md.digest();
          final StringBuilder sb = new StringBuilder(128);
          for (final int b : digest) {
            sb.append(Character.forDigit(b >> 4 & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
          }
          return sb.toString();
        }
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static List<File> listJavaFiles(File parent) {
    if (parent.isFile()) {
      parent = parent.getParentFile();
    }

    final List<File> list = new ArrayList<>(8);
    final File[] files = parent.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (file.isFile() && isJavaFile(file)) {
          list.add(file);
        }
      }
    }
    return list;
  }

  @SuppressWarnings("try")
  public static List<File> collectFiles(final File root, final String ext) {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.collectFiles")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("root", root.getPath())
              .put("ext", ext)
              .build("args"));

      if (!root.exists()) {
        return Collections.emptyList();
      }

      try (final Stream<Path> stream = Files.walk(root.toPath())) {
        return stream
            .map(Path::toFile)
            .filter(file -> file.isFile() && file.getName().endsWith(ext))
            .collect(Collectors.toList());
      } catch (UncheckedIOException e) {
        IOException cause = e.getCause();
        if (cause instanceof AccessDeniedException) {
          return Collections.emptyList();
        }
        throw e;
      } catch (IOException e) {
        log.warn("@@ {}", e.getMessage());
        throw new UncheckedIOException(e);
      }
    }
  }

  @SuppressWarnings("try")
  public static Optional<File> collectFile(final File root, final String ext) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.collectFile")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("root", root.getPath())
              .put("ext", ext)
              .build("args"));

      try (final Stream<Path> stream = Files.walk(root.toPath())) {
        return stream
            .map(Path::toFile)
            .filter(file -> file.isFile() && file.getName().endsWith(ext))
            .findFirst();
      }
    }
  }

  public static boolean filterFile(final File file) {
    final Config config = Config.load();

    final List<String> include = config.getIncludeList();
    final List<String> exclude = config.getExcludeList();
    final String path = file.getPath();
    if (include.size() > 0) {
      for (final String in : include) {
        if (path.matches(in)) {
          log.debug("match include {}:{}", in, path);
          return true;
        }
      }
      return false;
    }
    if (exclude.size() > 0) {
      for (final String ex : exclude) {
        if (path.matches(ex)) {
          log.debug("match exclude {}:{}", ex, path);
          return false;
        }
      }
      return true;
    }
    return true;
  }

  @SuppressWarnings("try")
  public static String findProjectID(final File root, final String target) throws IOException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.findProjectID")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("root", root.getPath())
              .put("target", target)
              .build("args"));
      MessageDigest md;
      try {
        md = MessageDigest.getInstance(ALGORITHM_SHA_512);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      Files.walkFileTree(
          root.toPath(),
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
                throws IOException {
              final File file = path.toFile();
              if (file.getName().equals(target)) {
                byte[] buf = new byte[8192];
                int readByte;
                try (FileInputStream in = new FileInputStream(file)) {
                  while ((readByte = in.read(buf)) != -1) {
                    md.update(buf, 0, readByte);
                  }
                }
              }
              return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(128);
      for (final int b : digest) {
        sb.append(Character.forDigit(b >> 4 & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    }
  }

  public static String getVersionInfo() throws IOException {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final Properties properties = new Properties();
    properties.load(classLoader.getResourceAsStream("VERSION"));
    return properties.getProperty("version");
  }

  @SuppressWarnings("try")
  public static Optional<File> getSourceFile(final String importClass, final Set<File> sourceRoots)
      throws IOException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.getSourceFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("importClass", importClass)
              .put("sourceRoots", sourceRoots.toString())
              .build("args"));
      final String path = ClassNameUtils.replaceDot2FileSep(importClass) + JAVA_EXT;
      for (final File root : sourceRoots) {
        final Path p = Paths.get(root.getCanonicalPath(), path);
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
          return Optional.of(p.toFile());
        }
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  private static boolean hasClassFile(
      final String path, final Set<File> sourceRoots, final File out) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.hasClassFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("sourceRoot", sourceRoots.toString())
              .put("out", out.getPath())
              .build("args"));
      final String outPath = out.getCanonicalPath();
      for (final File rootFile : sourceRoots) {
        final String root = rootFile.getCanonicalPath();
        if (path.startsWith(root)) {
          final String src = path.substring(root.length());
          final String classFile = StringUtils.replace(src, JAVA_EXT, CLASS_EXT);
          final Path p = Paths.get(outPath, classFile);
          return Files.exists(p, LinkOption.NOFOLLOW_LINKS);
        }
      }
      return false;
    }
  }

  @SuppressWarnings("try")
  public static Optional<File> getClassFile(
      String path, final Set<File> sourceRoots, final File out) throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.getClassFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("sourceRoot", sourceRoots.toString())
              .put("out", out.getPath())
              .build("args"));
      String outPath = out.getCanonicalPath();
      for (File rootFile : sourceRoots) {
        final String root = rootFile.getCanonicalPath();
        if (path.startsWith(root)) {
          final String src = path.substring(root.length());
          final String classFile = StringUtils.replace(src, JAVA_EXT, CLASS_EXT);
          final Path p = Paths.get(outPath, classFile);
          if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(p.toFile());
          }
        }
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  public static Collection<File> getPackagePrivateSource(final List<File> compileFiles) {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.getPackagePrivateSource")) {
      final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<>(8));
      compileFiles.parallelStream().forEach(file -> temp.addAll(FileUtils.listJavaFiles(file)));
      return temp;
    }
  }

  @SuppressWarnings("try")
  public static List<File> getModifiedSources(
      final File projectRoot,
      final List<File> sourceFiles,
      final Set<File> sourceRoots,
      final File output)
      throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.getModifiedSources")) {

      String projectRootPath = projectRoot.getCanonicalPath();
      final Map<String, String> map = ProjectDatabaseHelper.getChecksumMap(projectRootPath);

      final List<File> fileList =
          sourceFiles
              .parallelStream()
              .filter(
                  f -> {
                    if (!FileUtils.isJavaFile(f)) {
                      return false;
                    }
                    try {
                      String fileName = f.getName();
                      final String path = f.getCanonicalPath();
                      if (!fileName.equals(PACKAGE_INFO_JAVA)
                          && !FileUtils.hasClassFile(path, sourceRoots, output)) {
                        return true;
                      }

                      final String md5sum = FileUtils.getChecksum(f);
                      if (map.containsKey(path)) {
                        // compare checksum
                        final String prevSum = map.get(path);
                        if (md5sum.equals(prevSum)) {
                          // not modify
                          return false;
                        }
                      }
                      map.put(path, md5sum);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      boolean b = ProjectDatabaseHelper.saveChecksumMap(projectRootPath, map);
      log.debug("remove unmodified {} to {}", sourceFiles.size(), fileList.size());
      log.trace("modified : {} {}", fileList, b);
      return fileList;
    }
  }

  private static String readFile(String path) throws IOException {
    final byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, StandardCharsets.UTF_8);
  }

  @SuppressWarnings("try")
  public static List<String> readLines(File file) throws IOException {
    try (TelemetryUtils.ScopedSpan scope = TelemetryUtils.startScopedSpan("FileUtils.readLines")) {
      List<String> lines;
      try (InputStream in = new FileInputStream(file)) {
        lines = IOUtils.readLines(in);
      }
      if (isNull(lines) || lines.isEmpty()) {
        return Collections.emptyList();
      }
      return lines;
    }
  }

  @SuppressWarnings("try")
  public static void readRangeLines(File file, long start, long end, Consumer<String> fn)
      throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.readRangeLines")) {

      try (final BufferedReader br =
          new BufferedReader(
              new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
        String s;
        long i = 0;
        while ((s = br.readLine()) != null) {
          if (start > i) {
            continue;
          }
          if (end >= 0 && end <= i) {
            return;
          }
          fn.accept(s);
          i++;
        }
      }
    }
  }

  @SuppressWarnings("try")
  public static void formatJavaFile(final Properties properties, final String path)
      throws IOException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.formatJavaFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      final String content = readFile(path);
      final String formatted = JavaFormatter.formatEclipseStyle(properties, content);
      Files.write(
          Paths.get(path),
          formatted.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);
    }
  }

  @SuppressWarnings("try")
  public static void formatJavaFile(final String path) throws IOException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.formatJavaFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      final String content = readFile(path);
      final String formatted = JavaFormatter.formatGoogleStyle(content);
      Files.write(
          Paths.get(path),
          formatted.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);
    }
  }

  public static Properties loadPropertiesFile(final File file) throws IOException {
    if (!file.exists()) {
      return null;
    }
    try (final FileInputStream in = new FileInputStream(file)) {
      final Properties properties = new Properties();
      properties.load(in);
      return properties;
    }
  }

  @SuppressWarnings("try")
  public static Optional<File> existsFQCN(final Set<File> roots, final String fqcn) {
    try (TelemetryUtils.ScopedSpan scope = TelemetryUtils.startScopedSpan("FileUtils.existsFQCN")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("roots", roots.toString())
              .put("fqcn", fqcn)
              .build("args"));
      Optional<String> result = GlobalCache.getInstance().getSourceMap(fqcn);
      if (result.isPresent()) {
        return result.map(File::new);
      }
      return roots
          .parallelStream()
          .map(root -> convertFQCNToFile(root, fqcn))
          .filter(File::exists)
          .findFirst();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      log.catching(t);
      ErrorReporter.report(t);
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  private static File convertFQCNToFile(final File root, final String fqcn) {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("FileUtils.convertFQCNToFile")) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("roots", root.getPath()).build("args"));
      final String clazzName = ClassNameUtils.getParentClass(fqcn);
      final String path = StringUtils.replace(clazzName, ".", File.separator) + FileUtils.JAVA_EXT;
      return new File(root, path);
    }
  }

  @SuppressWarnings("try")
  public static Optional<Source> getSource(final File file) throws IOException, ExecutionException {
    try (TelemetryUtils.ScopedSpan scope = TelemetryUtils.startScopedSpan("FileUtils.getSource")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("file", file.getPath()).build("args"));

      final GlobalCache globalCache = GlobalCache.getInstance();
      return Optional.of(globalCache.getSource(file.getCanonicalFile()));
    }
  }

  @SuppressWarnings("try")
  public static Optional<String> convertPathToClass(final Set<File> roots, final File file)
      throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpanLow("FileUtils.convertPathToClass")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("roots", roots.toString())
              .put("file", file.getPath())
              .build("args"));

      for (File root : roots) {
        Optional<String> s = convertPathToClass(root, file);
        if (s.isPresent()) {
          return s;
        }
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("try")
  private static Optional<String> convertPathToClass(final File root, final File file)
      throws IOException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpanLow("FileUtils.convertPathToClass")) {

      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("root", root.getPath())
              .put("file", file.getPath())
              .build("args"));

      String rootPath = root.getCanonicalPath();
      String path = file.getCanonicalPath();
      if (path.startsWith(rootPath)) {
        String part = path.substring(rootPath.length());
        int i = part.lastIndexOf('.');
        if (i > 0) {
          part = part.substring(0, i);
        }
        String replaced = StringUtils.replace(part, File.separator, ".");
        if (replaced.startsWith(".")) {
          replaced = replaced.substring(1);
        }
        return Optional.of(replaced);
      }
      return Optional.empty();
    }
  }
}
