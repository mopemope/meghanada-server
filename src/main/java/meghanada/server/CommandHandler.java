package meghanada.server;

import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.location.Location;
import meghanada.reflect.CandidateUnit;
import meghanada.session.Session;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static meghanada.utils.FunctionUtils.wrapIOConsumer;

public class CommandHandler {

    private static final Logger log = LogManager.getLogger(CommandHandler.class);

    private final Session session;
    private final BufferedWriter writer;
    private final OutputFormatter outputFormatter;

    public CommandHandler(@Nonnull final Session session,
                          @Nonnull final BufferedWriter writer,
                          @Nonnull final OutputFormatter formatter) {
        this.session = session;
        this.writer = writer;
        this.outputFormatter = formatter;
    }

    public void changeProject(final String path) {
        try {
            final String canonicalPath = new File(path).getCanonicalPath();
            final boolean result = session.changeProject(canonicalPath);
            final String out = outputFormatter.changeProject(result);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void diagnostics(final String path) {
        try {
            final String canonicalPath = new File(path).getCanonicalPath();
            final CompileResult compileResult = session.compileProject();
            final String out = outputFormatter.diagnostics(compileResult, canonicalPath);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void compile(final String path) {
        try {
            final String canonicalPath = new File(path).getCanonicalPath();
            final CompileResult compileResult = session.compileFile(canonicalPath);
            final String out = outputFormatter.compile(compileResult, canonicalPath);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void compileProject() {
        try {
            final CompileResult compileResult = session.compileProject();
            final String out = outputFormatter.compileProject(compileResult);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void autocomplete(final String path, final String line, final String column, final String prefix) {
        try {
            final int lineInt = Integer.parseInt(line);
            final int columnInt = Integer.parseInt(column);
            final Collection<? extends CandidateUnit> units = session.completionAt(path, lineInt, columnInt, prefix);
            final String out = outputFormatter.autocomplete(units);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void runJUnit(final String test) {
        try (final InputStream in = this.session.runJUnit(test)) {
            final byte[] buf = new byte[1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                writer.write(new String(buf, 0, read, StandardCharsets.UTF_8));
                writer.flush();
            }
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }

    }

    public void parse(final String path) {
        try {
            final boolean result = session.parseFile(path);
            final String out = outputFormatter.parse(result);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void addImport(final String path, final String fqcn) {
        try {
            final boolean result = session.addImport(path, fqcn);
            final String out = outputFormatter.addImport(result, ClassNameUtils.replaceInnerMark(fqcn));
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }

    }

    public void optimizeImport(final String path) {
        try {
            final List<String> result = session.optimizeImport(path);
            final String out = outputFormatter.optimizeImport(result);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void importAll(final String path) {
        try {
            final Map<String, List<String>> result = session.searchMissingImport(path);
            final String out = outputFormatter.importAll(result);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void switchTest(final String path) {
        try {
            final String openPath = session.switchTest(path).orElse(path);
            final String out = outputFormatter.switchTest(openPath);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void ping() {
        try {
            writer.write("pong");
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void jumpDeclaration(final String path, final String line, final String col, final String symbol) {
        final int lineInt = Integer.parseInt(line);
        final int columnInt = Integer.parseInt(col);
        try {
            final Location location = session.jumpDeclaration(path, lineInt, columnInt, symbol)
                    .orElseGet(() -> new Location(path, lineInt, columnInt));
            final String out = outputFormatter.jumpDeclaration(location);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }

    }

    public void backJump() {
        final Location location = session.backDeclaration();
        try {
            if (location != null) {
                final String out = outputFormatter.jumpDeclaration(location);
                writer.write(out);
            }
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void runTask(final List<String> args) {
        try (final InputStream in = this.session.runTask(args)) {
            final byte[] buf = new byte[512];
            int read;
            while ((read = in.read(buf)) > 0) {
                writer.write(new String(buf, 0, read, StandardCharsets.UTF_8));
                writer.flush();
            }
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void clearCache() {
        try {
            final boolean result = this.session.clearCache();
            final String out = outputFormatter.clearCache(result);
            writer.write(out);
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }

    }

    public void localVariable(final String path, final String line) {
        final int lineInt = Integer.parseInt(line);
        try {
            final Optional<LocalVariable> localVariable = session.localVariable(path, lineInt);
            localVariable.ifPresent(wrapIOConsumer(lv -> {
                final String out = outputFormatter.localVariable(lv);
                writer.write(out);
            }));
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void formatCode(String path) {
        try {
            final String canonicalPath = new File(path).getCanonicalPath();
            session.formatCode(canonicalPath);
            writer.write(outputFormatter.formatCode(canonicalPath));
            writer.newLine();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }

    public void showDeclaration(final String path, final String line, final String col, final String symbol) {
        final int lineInt = Integer.parseInt(line);
        final int columnInt = Integer.parseInt(col);
        try {
            final Declaration declaration = session.showDeclaration(path, lineInt, columnInt, symbol)
                    .orElse(new Declaration("", "", Declaration.Type.OTHER, 0));
            final String out = outputFormatter.showDeclaration(declaration);
            writer.write(out);
            writer.newLine();
            writer.flush();
        } catch (Throwable e) {
            log.catching(e);
            throw new CommandException(e);
        }
    }
}
