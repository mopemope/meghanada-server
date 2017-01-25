package meghanada.utils;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import meghanada.config.Config;
import meghanada.reflect.asm.CachedASMReflector;
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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class FileUtils {

    private static final Logger log = LogManager.getLogger(FileUtils.class);

    public static boolean isJavaFile(final File file) {
        return file.getName().endsWith(".java") && file.exists();
    }

    public static String md5sum(final File file) throws IOException {
        final EntryMessage entryMessage = log.traceEntry("file={}", file);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = Files.newInputStream(file.toPath());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            final byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
            }
            final byte[] digest = md.digest();
            final StringBuilder sb = new StringBuilder();
            for (final int b : digest) {
                sb.append(Character.forDigit(b >> 4 & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return log.traceExit(entryMessage, sb.toString());
        }
    }

    public static List<File> listJavaFiles(File parent) {
        if (parent.isFile()) {
            parent = parent.getParentFile();
        }

        final List<File> list = new ArrayList<>();
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

    public static void deleteFile(final File root, final boolean deleteRoot) throws IOException {
        Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
            md = MessageDigest.getInstance("MD5");
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
        StringBuilder sb = new StringBuilder();
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

    public static File getSettingFile(final String fileName) {
        final String settingDir = Config.load().getProjectSettingDir();
        final File dir = new File(settingDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("fail create directory={}", dir);
        }
        return new File(dir, fileName);
    }

    public static synchronized Map<String, String> readMapSetting(final File inFile) throws IOException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        try {
            return reflector.getKryoPool().run(kryo -> {
                try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(inFile), 8192)))) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = kryo.readObject(input, HashMap.class);
                    return map;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }
    }

    public static synchronized void writeMapSetting(final Map<String, String> map, final File outFile) throws IOException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        try {
            reflector.getKryoPool().run(kryo -> {
                try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(outFile), 8192)))) {
                    kryo.writeObject(output, map);
                    return map;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }
    }

    public static String toHashedPath(final File f, String suffix) throws IOException {
        final String path = f.getCanonicalPath();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(path.getBytes("UTF-8"));
        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder();
        for (final int b : digest) {
            sb.append(Character.forDigit(b >> 4 & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        sb.append(suffix);
        return sb.toString();
    }

    public static Optional<File> getSourceFile(final String importClass, final Set<File> sourceRoots) {
        final String p = ClassNameUtils.replaceDot2FileSep(importClass) + ".java";
        for (final File root : sourceRoots) {
            // TODO slow
            final File file = new File(root, p);
            if (file.exists()) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    public static boolean hasClassFile(String path, Set<File> sourceRoots, File out) throws IOException {
        if (sourceRoots == null) {
            return false;
        }
        for (final File rootFile : sourceRoots) {
            final String root = rootFile.getCanonicalPath();
            if (path.startsWith(root)) {
                // find
                final String src = path.substring(root.length());
                final String classFile = ClassNameUtils.replace(src, ".java", ".class");
                final File file = new File(out, classFile);
                return file.exists();
            }
        }
        return false;
    }

    private static List<File> getJavaSource(final File root) throws IOException {
        return Files.walk(root.toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toFile().isFile() && path.endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());

    }

    public static List<File> getPackagePrivateSource(final List<File> compileFiles) {
        final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>());

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

    public static List<File> getModifiedSources(final String filePath, final List<File> sourceFiles, final Set<File> sourceRoots, final File output) throws IOException {

        final File checksumFile = FileUtils.getSettingFile(filePath);
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

                        final String md5sum = FileUtils.md5sum(f);
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

    public static String readFile(String path) throws IOException {
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

}
