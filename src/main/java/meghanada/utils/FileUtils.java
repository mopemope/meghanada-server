package meghanada.utils;

import meghanada.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class FileUtils {

    private static final Logger log = LogManager.getLogger(FileUtils.class);

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
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int b : digest) {
                sb.append(Character.forDigit(b >> 4 & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return log.traceExit(entryMessage, sb.toString());
        }
    }

    public static void deleteFile(final File file) throws IOException {
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
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

}
