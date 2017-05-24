package meghanada.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meghanada.Main;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.formatter.JavaFormatter;
import meghanada.project.Project;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

public final class FileUtils {

  public static final String JAVA_EXT = ".java";
  public static final String JAR_EXT = ".jar";
  public static final String CLASS_EXT = ".class";
  private static final Logger log = LogManager.getLogger(FileUtils.class);
  private static final String ALGORITHM_SHA_512 = "SHA-512";
  private static final String UTF_8 = "UTF-8";

  public static boolean isJavaFile(final File file) {
    return file.getName().endsWith(JAVA_EXT) && file.exists();
  }

  public static String getChecksum(final File file) throws IOException {
    final EntryMessage entryMessage = log.traceEntry("file={}", file);
    try {
      final MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA_512);
      try (final InputStream is = Files.newInputStream(file.toPath());
          DigestInputStream dis = new DigestInputStream(is, md)) {
        final byte[] buf = new byte[8192];
        while (dis.read(buf) != -1) {}
        final byte[] digest = md.digest();
        final StringBuilder sb = new StringBuilder(128);
        for (final int b : digest) {
          sb.append(Character.forDigit(b >> 4 & 0xF, 16));
          sb.append(Character.forDigit(b & 0xF, 16));
        }
        log.traceExit(entryMessage);
        return sb.toString();
      }
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<File> listJavaFiles(File parent) {
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

  public static List<File> collectFiles(final File root, final String ext) {
    if (!root.exists()) {
      return Collections.emptyList();
    }
    try (final Stream<Path> stream = Files.walk(root.toPath())) {
      return stream
          .map(Path::toFile)
          .filter(file -> file.isFile() && file.getName().endsWith(ext))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Optional<File> collectFile(final File root, final String ext) throws IOException {
    try (final Stream<Path> stream = Files.walk(root.toPath())) {
      return stream
          .map(Path::toFile)
          .filter(file -> file.isFile() && file.getName().endsWith(ext))
          .findFirst();
    }
  }

  public static void deleteFiles(final File root, final boolean deleteRoot) throws IOException {
    if (!root.exists()) {
      return;
    }
    Files.walkFileTree(
        root.toPath(),
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
              throws IOException {
            if (deleteRoot) {
              Files.delete(dir);
            } else {
              if (!dir.toFile().equals(root)) {
                Files.delete(dir);
              }
            }

            return FileVisitResult.CONTINUE;
          }
        });
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

  public static String findProjectID(final File root, final String target) throws IOException {
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
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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

  public static String getVersionInfo() throws IOException {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final Properties properties = new Properties();
    properties.load(classLoader.getResourceAsStream("VERSION"));
    return properties.getProperty("version");
  }

  public static File getProjectDataFile(final File projectRoot, final String key)
      throws IOException {
    final File settingDirFile = new File(projectRoot, Config.MEGHANADA_DIR);
    final File root = new File(settingDirFile, GlobalCache.DATA_DIR);
    if (!root.exists() && !root.mkdirs()) {
      log.warn("fail create directory={}", root);
    }
    final String path =
        FileUtils.toHashedPath(
            settingDirFile.getCanonicalPath() + ':' + key, GlobalCache.CACHE_EXT);
    return new File(root, path);
  }

  @SuppressWarnings("unchecked")
  public static synchronized Map<String, String> readMapSetting(final File inFile) {
    final GlobalCache globalCache = GlobalCache.getInstance();
    return globalCache.readCacheFromFile(inFile, HashMap.class);
  }

  public static synchronized void writeMapSetting(
      final Map<String, String> map, final File outFile) {
    final GlobalCache globalCache = GlobalCache.getInstance();
    globalCache.asyncWriteCache(outFile, map);
  }

  public static String toHashedPath(final File f, final String suffix) throws IOException {
    final String path = f.getCanonicalPath();
    return toHashedPath(path, suffix);
  }

  public static String toHashedPath(final String path, final String suffix) {
    try {
      final MessageDigest md = MessageDigest.getInstance(ALGORITHM_SHA_512);
      md.update(path.getBytes(UTF_8));
      md.update(Main.getVersion().getBytes(UTF_8));
      final byte[] digest = md.digest();
      final StringBuilder sb = new StringBuilder(128);
      for (final int b : digest) {
        sb.append(Character.forDigit(b >> 4 & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      sb.append(suffix);
      return sb.toString();
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static Optional<File> getSourceFile(final String importClass, final Set<File> sourceRoots)
      throws IOException {

    final String path = ClassNameUtils.replaceDot2FileSep(importClass) + JAVA_EXT;
    for (final File root : sourceRoots) {
      final Path p = Paths.get(root.getCanonicalPath(), path);
      if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.of(p.toFile());
      }
    }
    return Optional.empty();
  }

  private static boolean hasClassFile(
      final String path, final Set<File> sourceRoots, final File out) throws IOException {

    final String outPath = out.getCanonicalPath();
    for (final File rootFile : sourceRoots) {
      final String root = rootFile.getCanonicalPath();
      if (path.startsWith(root)) {
        final String src = path.substring(root.length());
        final String classFile = ClassNameUtils.replace(src, JAVA_EXT, CLASS_EXT);
        final Path p = Paths.get(outPath, classFile);
        return Files.exists(p, LinkOption.NOFOLLOW_LINKS);
      }
    }
    return false;
  }

  public static List<File> getPackagePrivateSource(final List<File> compileFiles) {
    final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(8));

    compileFiles
        .parallelStream()
        .forEach(
            file -> {
              if (file.isFile()) {
                final List<File> list = FileUtils.listJavaFiles(file.getParentFile());
                temp.addAll(list);
              } else {
                final List<File> list = FileUtils.listJavaFiles(file);
                temp.addAll(list);
              }
            });
    return new ArrayList<>(temp);
  }

  public static List<File> getModifiedSources(
      final File projectRoot,
      final List<File> sourceFiles,
      final Set<File> sourceRoots,
      final File output)
      throws IOException {

    final File checksumFile =
        FileUtils.getProjectDataFile(projectRoot, GlobalCache.SOURCE_CHECKSUM_DATA);
    final Config config = Config.load();
    final Map<String, String> map = config.getChecksumMap(checksumFile);

    final List<File> fileList =
        sourceFiles
            .parallelStream()
            .filter(
                f -> {
                  if (!FileUtils.isJavaFile(f)) {
                    return false;
                  }
                  try {
                    final String path = f.getCanonicalPath();
                    if (!FileUtils.hasClassFile(path, sourceRoots, output)) {
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
                      // update
                      map.put(path, md5sum);
                    } else {
                      // save checksum
                      map.put(path, md5sum);
                    }
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                  return true;
                })
            .collect(Collectors.toList());

    FileUtils.writeMapSetting(map, checksumFile);
    config.getAllChecksumMap().put(checksumFile, map);

    log.debug("remove unmodified {} To {}", sourceFiles.size(), fileList.size());
    log.trace("modified : {}", fileList);
    return fileList;
  }

  public static void invalidateChecksum(final File projectRoot, final String path)
      throws IOException {

    final File checksumFile =
        FileUtils.getProjectDataFile(projectRoot, GlobalCache.SOURCE_CHECKSUM_DATA);
    final Config config = Config.load();
    final Map<String, String> map = config.getChecksumMap(checksumFile);
    map.remove(path);
    FileUtils.writeMapSetting(map, checksumFile);
    config.getAllChecksumMap().put(checksumFile, map);
  }

  private static String readFile(String path) throws IOException {
    final byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, StandardCharsets.UTF_8);
  }

  public static void formatJavaFile(final Properties properties, final String path)
      throws IOException {
    final String content = readFile(path);
    final String formatted = JavaFormatter.formatEclipseStyle(properties, content);
    Files.write(
        Paths.get(path),
        formatted.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static void formatJavaFile(final String path) throws IOException {
    final String content = readFile(path);
    final String formatted = JavaFormatter.formatGoogleStyle(content);
    Files.write(
        Paths.get(path),
        formatted.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
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

  public static Optional<File> existsFQCN(final Set<File> roots, final String fqcn) {
    return roots
        .parallelStream()
        .map(root -> convertFQCNToFile(root, fqcn))
        .filter(File::exists)
        .findFirst();
  }

  private static File convertFQCNToFile(final File root, final String fqcn) {
    final String clazzName = ClassNameUtils.getParentClass(fqcn);
    final String path = ClassNameUtils.replace(clazzName, ".", File.separator) + FileUtils.JAVA_EXT;
    return new File(root, path);
  }

  public static Optional<Source> getSource(final Project project, final File file)
      throws IOException, ExecutionException {
    final GlobalCache globalCache = GlobalCache.getInstance();
    return Optional.of(globalCache.getSource(project, file.getCanonicalFile()));
  }
}
