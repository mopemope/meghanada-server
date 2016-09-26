package meghanada.server;

import meghanada.compiler.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.location.Location;
import meghanada.reflect.CandidateUnit;
import meghanada.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommandHandler {

    private static final Logger log = LogManager.getLogger(CommandHandler.class);

    private final Session session;
    private final BufferedWriter writer;
    private final OutputFormatter formatter;

    public CommandHandler(Session session, BufferedWriter writer, OutputFormatter formatter) {
        this.session = session;
        this.writer = writer;
        this.formatter = formatter;
    }

    public void changeProject(String path) {
        try {
            path = new File(path).getCanonicalPath();
            final boolean result = session.changeProject(path);
            final String out = formatter.changeProject(result);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void diagnostics(String path) {
        try {
            path = new File(path).getCanonicalPath();
            final CompileResult compileResult = session.compileProject();
            final String out = formatter.diagnostics(compileResult, path);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void compile(String path) {
        try {
            path = new File(path).getCanonicalPath();
            final CompileResult compileResult = session.compileFile(path);
            final String out = formatter.compile(compileResult, path);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void compileProject() {
        try {
            final CompileResult compileResult = session.compileProject();
            final String out = formatter.compileProject(compileResult);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void autocomplete(String path, String line, String column, String prefix) {
        // String path line column ^String prefix fmt]
        try {
            int lineInt = Integer.parseInt(line);
            int columnInt = Integer.parseInt(column);
            final Collection<? extends CandidateUnit> units = session.completionAt(path, lineInt, columnInt, prefix);
            final String out = formatter.autocomplete(units);

            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void runJUnit(String test) {
        try (InputStream in = this.session.runJUnit(test)) {
            byte[] buf = new byte[512];
            int read;
            while ((read = in.read(buf)) > 0) {
                writer.write(new String(buf, 0, read, Charset.forName("UTF8")));
                writer.flush();
            }
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void parse(String path) {
        try {
            boolean result = session.parseFile(path);
            final String out = formatter.parse(result);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addImport(String path, String fqcn) {
        try {
            boolean result = session.addImport(path, fqcn);
            final String out = formatter.addImport(result);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void optimizeImport(String path) {
        try {
            final List<String> result = session.optimizeImport(path);
            final String out = formatter.optimizeImport(result);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void importAll(String path) {
        try {
            final Map<String, List<String>> result = session.searchMissingImport(path);
            final String out = formatter.importAll(result);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void switchTest(String path) {
        try {
            final String openPath = session.switchTest(path);
            final String out = formatter.switchTest(openPath);
            writer.write(out);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void ping() {
        try {
            writer.write("pong");
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void jumpDeclaration(String path, String line, String col, String symbol) {
        int lineInt = Integer.parseInt(line);
        int columnInt = Integer.parseInt(col);
        try {
            Location location = session.jumpDeclaration(path, lineInt, columnInt, symbol);
            if (location != null) {
                String out = formatter.jumpDeclaration(location);
                writer.write(out);
            }
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void backJump() {
        Location location = session.backDeclaration();
        try {
            if (location != null) {
                String out = formatter.jumpDeclaration(location);
                writer.write(out);
            }
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void runTask(List<String> args) {
        try (InputStream in = this.session.runTask(args)) {
            byte[] buf = new byte[512];
            int read;
            while ((read = in.read(buf)) > 0) {
                writer.write(new String(buf, 0, read, Charset.forName("UTF8")));
                writer.flush();
            }
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void clearCache() {
        try {
            final boolean result = Session.clearCache();
            final String out = formatter.clearCache(result);
            writer.write(out);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void localVariable(final String path, final String line) {
        final int lineInt = Integer.parseInt(line);
        try {
            final LocalVariable lv = session.localVariable(path, lineInt);
            if (lv != null) {
                final String out = formatter.localVariable(lv);
                writer.write(out);
            }
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
