package meghanada;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class SetupMain {

  public static final String VERSION = "0.0.2";
  private static final String OPT_SERVER_VERSION = "server-version";
  private static final String OPT_DEST = "dest";
  private static final String OPT_SIMPLE = "simple";

  private static String version;
  private static boolean useSimple;

  public static String getVersion() throws IOException {
    if (version != null) {
      return version;
    }
    final String v = getVersionInfo();
    if (v != null) {
      version = v;
      return version;
    }
    version = SetupMain.VERSION;
    return version;
  }

  public static void main(String[] args) throws ParseException, IOException {
    final String version = getVersion();
    System.setProperty("meghanada-setup.version", version);
    final Options options = buildOptions();

    final CommandLineParser parser = new DefaultParser();
    final CommandLine cmd = parser.parse(options, args);
    HelpFormatter formatter = new HelpFormatter();

    if (cmd.hasOption("h")) {
      formatter.printHelp("meghanada setup : " + version, options);
      return;
    }
    if (cmd.hasOption("version")) {
      System.out.println(version);
      return;
    }
    if (cmd.hasOption("simple")) {
      useSimple = true;
    }

    if (cmd.hasOption(OPT_SERVER_VERSION)) {
      final String serverVersion = cmd.getOptionValue(OPT_SERVER_VERSION, "");
      final String destRoot = cmd.getOptionValue(OPT_DEST, "");
      if (!version.isEmpty()) {
        System.setProperty("meghanada.server-version", serverVersion);
      }
      run(destRoot, serverVersion);
      return;
    }
    System.out.println("server-version is required");
    formatter.printHelp("meghanada setup : " + version, options);
  }

  private static void run(String destRoot, String version) throws IOException {
    try {
      downloadFromGithub(destRoot, version);
    } catch (IOException e) {
      downloadFromBintray(destRoot, version);
    }
  }

  private static void downloadFromBintray(String destRoot, String version) throws IOException {
    String downloadURL = getBintrayURL(version);
    Path dest = copyDest(destRoot, version);
    downloadJar(downloadURL, dest);
  }

  private static void downloadFromGithub(String destRoot, String version) throws IOException {
    String downloadURL = getGithubURL(version);
    Path dest = copyDest(destRoot, version);
    downloadJar(downloadURL, dest);
  }

  private static String getBintrayURL(String version) throws IOException {
    return String.format("https://dl.bintray.com/mopemope/meghanada/meghanada-%s.jar", version);
  }

  private static String getGithubURL(String version) throws IOException {
    return String.format(
        "https://github.com/mopemope/meghanada-server/releases/download/v%s/meghanada-%s.jar",
        version, version);
  }

  private static Path copyDest(String destRoot, String version) throws IOException {
    String home = System.getProperty("user.home");
    Path root = Paths.get(home, ".emacs.d", "meghanada");
    if (Objects.nonNull(destRoot) && !destRoot.isEmpty()) {
      root = FileSystems.getDefault().getPath(destRoot);
    }
    String file = String.format("meghanada-%s.jar", version);
    return root.resolve(file);
  }

  private static void downloadJar(String downloadURL, Path destPath) throws IOException {
    URL url = new URL(downloadURL);
    URLConnection connection = url.openConnection();
    long contentLength = connection.getContentLengthLong();
    System.out.println("download from " + downloadURL);
    try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
        OutputStream out =
            Files.newOutputStream(destPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      long nread = 0L;
      byte[] buf = new byte[1024 * 64];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
        nread += n;
        if (useSimple) {
          showSimpleProgress(contentLength, nread);
        } else {
          showProgress(contentLength, nread);
        }
      }
    }
    System.out.println("downloaded " + destPath);
    System.out.println("done");
  }

  private static void showSimpleProgress(long contentLength, long nread) {
    int percent = (int) (((double) nread / (double) contentLength) * 100);
    if (percent % 10 == 0) {
      String s = String.format(" %d/%d bytes", nread, contentLength);
      String per = String.format("downloaded %d%%", percent);
      System.out.println(per + s);
    }
  }

  private static void showProgress(long contentLength, long nread) {
    String s = String.format(" %d/%d bytes", nread, contentLength);
    String per =
        String.format("downloaded %d%%", (int) (((double) nread / (double) contentLength) * 100));
    System.out.print("\r" + per + s);
  }

  private static Options buildOptions() {
    final Options options = new Options();
    final Option help = new Option("h", "help", false, "show help");
    options.addOption(help);

    final Option version = new Option(null, "version", false, "show version information");
    options.addOption(version);

    final Option verbose = new Option("v", "verbose", false, "show verbose message (DEBUG)");
    options.addOption(verbose);

    final Option serverVersion =
        new Option(null, OPT_SERVER_VERSION, true, "set use server version (required)");
    options.addOption(serverVersion);

    final Option dest = new Option(null, OPT_DEST, true, "set download dest");
    options.addOption(dest);

    final Option simple = new Option(null, OPT_SIMPLE, false, "use simple message");
    options.addOption(simple);

    return options;
  }

  private static String getVersionInfo() throws IOException {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final Properties properties = new Properties();
    properties.load(classLoader.getResourceAsStream("VERSION"));
    return properties.getProperty("version");
  }
}
