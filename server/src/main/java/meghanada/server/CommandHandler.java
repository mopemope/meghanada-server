package meghanada.server;

import static java.util.Objects.isNull;

import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import meghanada.analyze.CompileResult;
import meghanada.completion.LocalVariable;
import meghanada.docs.declaration.Declaration;
import meghanada.index.SearchResults;
import meghanada.location.Location;
import meghanada.reference.Reference;
import meghanada.reflect.CandidateUnit;
import meghanada.session.Session;
import meghanada.typeinfo.TypeInfo;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandHandler {

  private static final Logger log = LogManager.getLogger(CommandHandler.class);

  private final Session session;
  private final BufferedWriter writer;
  private final OutputFormatter outputFormatter;

  public CommandHandler(
      final Session session, final BufferedWriter writer, final OutputFormatter formatter) {
    this.session = session;
    this.writer = writer;
    this.outputFormatter = formatter;
  }

  private void writeError(final long id, final Throwable t) {
    log.catching(t);
    try {
      final String out = outputFormatter.error(id, t);
      writer.write(out);
      writer.newLine();
    } catch (IOException e) {
      log.catching(e);
      throw new CommandException(e);
    }
  }

  public void changeProject(final long id, final String path) {
    try {
      final String canonicalPath = new File(path).getCanonicalPath();
      final boolean result = session.changeProject(canonicalPath);
      final String out = outputFormatter.changeProject(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void diagnostics(final long id, final String path) {
    File f = new File(path);
    try {
      String contents = org.apache.commons.io.FileUtils.readFileToString(f);
      final CompileResult compileResult = session.diagnosticString(path, contents);
      final String out = outputFormatter.diagnostics(id, compileResult, path);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void diagnostics(final long id, final String sourceFile, final String tmpSourceFile) {
    File f = new File(tmpSourceFile);
    f.deleteOnExit();
    try {
      String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
      final CompileResult compileResult = session.diagnosticString(sourceFile, contents);
      final String out = outputFormatter.diagnostics(id, compileResult, sourceFile);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      if (f.exists()) {
        f.delete();
      }
    }
  }

  public void compile(final long id, final String path) {
    try {
      final String canonicalPath = new File(path).getCanonicalPath();
      final CompileResult compileResult = session.compileFile(canonicalPath);
      final String out = outputFormatter.compile(id, compileResult, canonicalPath);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void compileProject(final long id, String path) {
    try {
      final CompileResult compileResult = session.compileProject(path, true);
      final String out = outputFormatter.compileProject(id, compileResult);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void autocomplete(
      final long id,
      final String path,
      final String line,
      final String column,
      final String prefix) {
    try {
      final int lineInt = Integer.parseInt(line);
      final int columnInt = Integer.parseInt(column);
      final Collection<? extends CandidateUnit> units =
          session.completionAt(path, lineInt, columnInt, prefix);
      final String out = outputFormatter.autocomplete(id, units);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void runJUnit(final long id, String path, final String test, boolean debug) {

    try (final BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                this.session.runJUnit(path, test, debug), StandardCharsets.UTF_8))) {

      String s;
      while ((s = reader.readLine()) != null) {
        if (!s.startsWith("SLF4J: ")) {
          writer.write(s);
          writer.newLine();
          writer.flush();
        }
      }

      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void parse(final long id, final String path) {

    try {
      final boolean result = session.parseFile(path);
      final String out = outputFormatter.parse(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void addImport(final long id, final String path, final String fqcn) {
    try {
      final boolean result = session.addImport(path, fqcn);
      final String out =
          outputFormatter.addImport(id, result, ClassNameUtils.replaceInnerMark(fqcn));
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void optimizeImport(final long id, final String sourceFile, final String tmpSourceFile) {
    File f = new File(tmpSourceFile);
    f.deleteOnExit();
    try {
      String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
      final String canonicalPath = f.getCanonicalPath();
      session.optimizeImport(sourceFile, tmpSourceFile, contents);
      writer.write(outputFormatter.optimizeImport(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void importAll(final long id, final String path) {
    try {
      final Map<String, List<String>> result = session.searchMissingImport(path);
      final String out = outputFormatter.importAll(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void switchTest(final long id, final String path) {
    try {
      final String openPath = session.switchTest(path).orElse(path);
      final String out = outputFormatter.switchTest(id, openPath);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void ping(final long id) {
    try {
      final String out = outputFormatter.ping(id, "pong");
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void jumpDeclaration(
      final long id, final String path, final String line, final String col, final String symbol) {
    final int lineInt = Integer.parseInt(line);
    final int columnInt = Integer.parseInt(col);
    try {
      final Location location =
          session
              .jumpDeclaration(path, lineInt, columnInt, symbol)
              .orElseGet(() -> new Location(path, lineInt, columnInt));
      final String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void backJump(final long id) {
    final Location location = session.backDeclaration().orElseGet(() -> new Location("", -1, -1));
    try {
      final String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void runTask(final long id, final List<String> args) {
    try (final InputStream in = this.session.runTask(args)) {
      String tasks = Joiner.on(" ").join(args);
      writer.write("run task: ");
      writer.write(tasks);
      writer.newLine();
      writer.newLine();

      final byte[] buf = new byte[512];
      int read;
      while ((read = in.read(buf)) > 0) {
        writer.write(new String(buf, 0, read, StandardCharsets.UTF_8));
        writer.flush();
      }
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void clearCache(final long id) {
    try {
      final boolean result = this.session.clearCache();
      final String out = outputFormatter.clearCache(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void localVariable(final long id, final String path, final String line) {
    final int lineInt = Integer.parseInt(line);
    try {
      final Optional<LocalVariable> localVariable = session.localVariable(path, lineInt);
      if (localVariable.isPresent()) {
        final String out = outputFormatter.localVariable(id, localVariable.get());
        writer.write(out);
      } else {
        final LocalVariable lv = new LocalVariable("void", Collections.emptyList());
        final String out = outputFormatter.localVariable(id, lv);
        writer.write(out);
      }
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void formatCode(final long id, final String path) {
    try {
      final String canonicalPath = new File(path).getCanonicalPath();
      session.formatCode(canonicalPath);
      writer.write(outputFormatter.formatCode(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void showDeclaration(
      final long id, final String path, final String line, final String col, final String symbol) {
    final int lineInt = Integer.parseInt(line);
    final int columnInt = Integer.parseInt(col);
    try {
      final Declaration declaration =
          session
              .showDeclaration(path, lineInt, columnInt, symbol)
              .orElse(new Declaration("", "", Declaration.Type.OTHER, 0));
      final String out = outputFormatter.showDeclaration(id, declaration);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void execMain(long id, String path, boolean debug) {
    InputStream in = null;
    try {
      in = this.session.execMain(path, debug);
      if (isNull(in)) {
        writer.write("missing main function");
        writer.newLine();
        writer.flush();
        return;
      }
    } catch (Throwable t) {
      writeError(id, t);
      return;
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

      String s;
      boolean start = false;
      StopWatch stopWatch = StopWatch.createStarted();
      while ((s = reader.readLine()) != null) {
        if (!s.startsWith("SLF4J: ")) {
          if (!start) {
            writer.write("run main: " + path);
            writer.newLine();
            start = true;
          }
          writer.write(s);
          writer.newLine();
          writer.flush();
        }
      }
      writer.write("fin: " + path);
      writer.newLine();
      writer.write("elapsed: " + stopWatch.toString());
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void reference(
      final long id, final String path, final String line, final String col, final String symbol) {
    final int lineInt = Integer.parseInt(line);
    final int columnInt = Integer.parseInt(col);
    try {
      List<Reference> references = session.reference(path, lineInt, columnInt, symbol);
      final String out = outputFormatter.references(id, references);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void typeInfo(
      final long id, final String path, final String line, final String col, final String symbol) {
    final int lineInt = Integer.parseInt(line);
    final int columnInt = Integer.parseInt(col);
    try {
      Optional<TypeInfo> typeInfo = session.typeInfo(path, lineInt, columnInt, symbol);
      if (typeInfo.isPresent()) {
        final String out = outputFormatter.typeInfo(id, typeInfo.get());
        writer.write(out);
      } else {
        TypeInfo dummy = new TypeInfo("");
        final String out = outputFormatter.typeInfo(id, dummy);
        writer.write(out);
      }

      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void searchEverywhere(final long id, final String q) {
    try {
      final Optional<SearchResults> results = session.searchEverywhere(q);
      if (results.isPresent()) {
        final String out = outputFormatter.searchEverywhere(id, results.get());
        writer.write(out);
      } else {
        SearchResults dummy = new SearchResults();
        final String out = outputFormatter.searchEverywhere(id, dummy);
        writer.write(out);
      }

      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void killRunningProcess(final long id) {
    try {
      session.killRunningProcess();
      final String out = outputFormatter.killRunningProcess(id);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void showProject(final long id) {
    try {
      final String s = session.showProject();
      final String out = outputFormatter.showProject(id, s);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void autocompleteResolve(
      long id, String path, String line, String column, String type, String item, String desc) {
    try {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      boolean b = session.completionResolve(path, lineInt, columnInt, type, item, desc);
      final String out = outputFormatter.completionResolve(id, b);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }

  public void importAtPoint(final long id, String path, String line, String column, String symbol) {
    try {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      final Map<String, List<String>> result =
          session.searchImports(path, lineInt, columnInt, symbol);
      final String out = outputFormatter.importAtPoint(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    }
  }
}
