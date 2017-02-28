package meghanada.utils;

import meghanada.Main;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.jboss.forge.roaster.Roaster;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
            try (InputStream is = Files.newInputStream(file.toPath());
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                final byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {
                }
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
        try {
            return Files.walk(root.toPath())
                    .map(Path::toFile)
                    .filter(file -> file.isFile() && file.getName().endsWith(ext))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public static Optional<File> collectFile(final File root, final String ext) throws IOException {
        return Files.walk(root.toPath())
                .map(Path::toFile)
                .filter(file -> file.isFile() && file.getName().endsWith(ext))
                .findFirst();
    }

    public static void deleteFiles(final File root, final boolean deleteRoot) throws IOException {
        if (!root.exists()) {
            return;
        }
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
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

        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
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

    public static File getProjectDataFile(final String key) {
        final String settingDir = Config.load().getProjectSettingDir();
        final File root = new File(settingDir, GlobalCache.DATA_DIR);
        if (!root.exists() && !root.mkdirs()) {
            log.warn("fail create directory={}", root);
        }
        final String path = FileUtils.toHashedPath(settingDir + ':' + key, GlobalCache.CACHE_EXT);
        return new File(root, path);
    }

    @SuppressWarnings("unchecked")
    public static synchronized Map<String, String> readMapSetting(final File inFile) throws IOException {
        final GlobalCache globalCache = GlobalCache.getInstance();
        return globalCache.readCacheFromFile(inFile, HashMap.class);
    }

    public static synchronized void writeMapSetting(final Map<String, String> map, final File outFile) throws IOException {
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
            md.update(Main.VERSION.getBytes(UTF_8));
            final byte[] digest = md.digest();
            final StringBuilder sb = new StringBuilder(128);
            for (final int b : digest) {
                sb.append(Character.forDigit(b >> 4 & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            sb.append(suffix);
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<File> getSourceFile(final String importClass, final Set<File> sourceRoots) {
        final String p = ClassNameUtils.replaceDot2FileSep(importClass) + JAVA_EXT;
        for (final File root : sourceRoots) {
            // TODO slow
            final File file = new File(root, p);
            if (file.exists()) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private static boolean hasClassFile(String path, Set<File> sourceRoots, File out) throws IOException {
        if (sourceRoots == null) {
            return false;
        }
        for (final File rootFile : sourceRoots) {
            final String root = rootFile.getCanonicalPath();
            if (path.startsWith(root)) {
                // find
                final String src = path.substring(root.length());
                final String classFile = ClassNameUtils.replace(src, JAVA_EXT, CLASS_EXT);
                final File file = new File(out, classFile);
                return file.exists();
            }
        }
        return false;
    }

    public static List<File> getPackagePrivateSource(final List<File> compileFiles) {
        final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(8));

        compileFiles.parallelStream().forEach(file -> {
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

    public static List<File> getModifiedSources(final List<File> sourceFiles, final Set<File> sourceRoots, final File output) throws IOException {

        final File checksumFile = FileUtils.getProjectDataFile(GlobalCache.SOURCE_CHECKSUM_DATA);
        final Config config = Config.load();
        final Map<String, String> map = config.getChecksumMap(checksumFile);

        final List<File> fileList = sourceFiles
                .parallelStream()
                .filter(f -> {
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
                }).collect(Collectors.toList());

        FileUtils.writeMapSetting(map, checksumFile);
        config.getAllChecksumMap().put(checksumFile, map);

        log.debug("remove unmodified {} To {}", sourceFiles.size(), fileList.size());
        log.trace("modified : {}", fileList);
        return fileList;
    }

    private static String readFile(String path) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    public static String formatJavaFile(final Properties properties, final String path) throws IOException {
        final String content = readFile(path);
        final String formatted = Roaster.format(properties, content);
        Files.write(Paths.get(path),
                formatted.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return formatted;
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
}
