package meghanada;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Gen12 {

    public void getChecksum(final File file) throws IOException {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(file.toPath());
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                final byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
