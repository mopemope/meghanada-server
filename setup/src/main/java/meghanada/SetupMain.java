package meghanada;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

  public static final String VERSION = "0.0.1";
  private static final String OPT_SERVER_VERSION = "server-version";
  private static final String OPT_DEST = "dest";

  private static String version;

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

  public static void main(String args[]) throws ParseException, IOException {
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
    String downloadURL = copyFrom(version);
    Path dest = copyDest(destRoot, version);
    downloadJar(downloadURL, dest);
  }

  private static String copyFrom(String version) throws IOException {
    return copyFromBintray(version);
  }

  private static String copyFromBintray(String version) throws IOException {
    return "https://dl.bintray.com/mopemope/meghanada/meghanada-" + version + ".jar";
  }

  private static Path copyDest(String destRoot, String version) throws IOException {
    String home = System.getProperty("user.home");
    Path root = Paths.get(home, ".emacs.d", "meghanada");
    if (Objects.nonNull(destRoot) && !destRoot.isEmpty()) {
      root = FileSystems.getDefault().getPath(destRoot);
    }
    String file = "meghanada-" + version + ".jar";
    return root.resolve(file);
  }

  private static void downloadJar(String downloadURL, Path destPath) throws IOException {
    URL url = new URL(downloadURL);
    try (BufferedInputStream in = new BufferedInputStream(url.openStream())) {
      Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
    }
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

    return options;
  }

  private static String getVersionInfo() throws IOException {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final Properties properties = new Properties();
    properties.load(classLoader.getResourceAsStream("VERSION"));
    return properties.getProperty("version");
  }
}
