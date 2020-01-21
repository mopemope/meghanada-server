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
import meghanada.telemetry.TelemetryUtils;
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

  private void writeError(long id, Throwable t) {
    log.catching(t);
    try {
      String out = outputFormatter.error(id, t);
      writer.write(out);
      writer.newLine();
    } catch (IOException e) {
      log.catching(e);
      throw new CommandException(e);
    }
  }

  @SuppressWarnings("try")
  public void changeProject(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/changeProject";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      boolean result = session.changeProject(canonicalPath);
      String out = outputFormatter.changeProject(id, result);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void diagnostics(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/diagnostics";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      File f = new File(path);
      String contents = org.apache.commons.io.FileUtils.readFileToString(f);
      CompileResult compileResult = session.diagnosticString(path, contents);
      String out = outputFormatter.diagnostics(id, compileResult, path);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void diagnostics(long id, String sourceFile, String tmpSourceFile) {
    long startTime = System.nanoTime();
    String name = "Meghanada/diagnostics/2";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("sourceFile", sourceFile)
              .put("tmpSourceFile", tmpSourceFile)
              .build("args"));

      File f = new File(tmpSourceFile);
      f.deleteOnExit();
      try {
        String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
        CompileResult compileResult = session.diagnosticString(sourceFile, contents);
        String out = outputFormatter.diagnostics(id, compileResult, sourceFile);
        writer.write(out);
        writer.newLine();
        span.setStatusOK();
      } finally {
        if (f.exists()) {
          f.delete();
        }
      }
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void compile(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/compile";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      CompileResult compileResult = session.compileFile(canonicalPath);
      String out = outputFormatter.compile(id, compileResult, canonicalPath);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void compileProject(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/compileProject";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      CompileResult compileResult = session.compileProject(path, true);
      String out = outputFormatter.compileProject(id, compileResult);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void autocomplete(long id, String path, String line, String column, String prefix) {
    long startTime = System.nanoTime();
    String name = "Meghanada/autocomplete";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("prefix", prefix)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Collection<? extends CandidateUnit> units =
          session.completionAt(path, lineInt, columnInt, prefix);
      String out = outputFormatter.autocomplete(id, units);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void runJUnit(long id, String path, String test, boolean debug) {
    long startTime = System.nanoTime();
    String name = "Meghanada/runJUnit";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("test", test)
              .put("debug", debug)
              .build("args"));
      try (BufferedReader reader =
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
      }
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void parse(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/parse";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      boolean result = session.parseFile(path);
      String out = outputFormatter.parse(id, result);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void addImport(long id, String path, String fqcn) {
    long startTime = System.nanoTime();
    String name = "Meghanada/addImport";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).put("fqcn", fqcn).build("args"));
      boolean result = session.addImport(path, fqcn);
      String out = outputFormatter.addImport(id, result, ClassNameUtils.replaceInnerMark(fqcn));
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void optimizeImport(long id, String sourceFile, String tmpSourceFile) {
    long startTime = System.nanoTime();
    String name = "Meghanada/optimizeImport";
    File f = new File(tmpSourceFile);
    f.deleteOnExit();
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/optimizeImport");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("sourceFile", sourceFile)
              .put("tmpSourceFile", tmpSourceFile)
              .build("args"));
      String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
      String canonicalPath = f.getCanonicalPath();
      session.optimizeImport(sourceFile, tmpSourceFile, contents);
      writer.write(outputFormatter.optimizeImport(id, canonicalPath));
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void importAll(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/importAll";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      Map<String, List<String>> result = session.searchMissingImport(path);
      String out = outputFormatter.importAll(id, result);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void switchTest(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/switchTest";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String openPath = session.switchTest(path).orElse(path);
      String out = outputFormatter.switchTest(id, openPath);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void ping(long id) {
    long startTime = System.nanoTime();
    String name = "Meghanada/ping";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      String out = outputFormatter.ping(id, "pong");
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void listSymbols(long id, boolean global) {
    long startTime = System.nanoTime();
    String name = "Meghanada/listSymbols";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("global", global).build("args"));
      String out = outputFormatter.listSymbols(id, String.join("\n", session.listSymbols(global)));
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void jumpSymbol(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/jumpSymbol";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("line", line).put("column", column).build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Location location =
          session
              .jumpSymbol(path, lineInt, columnInt, symbol)
              .orElseGet(() -> new Location(path, lineInt, columnInt));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void jumpDeclaration(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/jumpDeclaration";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Location location =
          session
              .jumpDeclaration(path, lineInt, columnInt, symbol)
              .orElseGet(() -> new Location(path, lineInt, columnInt));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void backJump(long id) {
    long startTime = System.nanoTime();
    String name = "Meghanada/backJump";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      Location location = session.backDeclaration().orElseGet(() -> new Location("", -1, -1));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void runTask(long id, List<String> tasks) {
    long startTime = System.nanoTime();
    String name = "Meghanada/runTask";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan());
        InputStream in = this.session.runTask(tasks)) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("tasks", tasks.toString()).build("args"));
      String taskStr = Joiner.on(" ").join(tasks);
      writer.write("run task: ");
      writer.write(taskStr);
      writer.newLine();
      writer.newLine();
      byte[] buf = new byte[512];
      int read;
      while ((read = in.read(buf)) > 0) {
        writer.write(new String(buf, 0, read, StandardCharsets.UTF_8));
        writer.flush();
      }
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void clearCache(long id) {
    long startTime = System.nanoTime();
    String name = "Meghanada/clearCache";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      boolean result = this.session.clearCache();
      String out = outputFormatter.clearCache(id, result);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void localVariable(long id, String path, String line) {
    long startTime = System.nanoTime();
    String name = "Meghanada/localVariable";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("line", line).build("args"));
      int lineInt = Integer.parseInt(line);
      Optional<LocalVariable> localVariable = session.localVariable(path, lineInt);
      if (localVariable.isPresent()) {
        String out = outputFormatter.localVariable(id, localVariable.get());
        writer.write(out);
      } else {
        LocalVariable lv = new LocalVariable("void", Collections.emptyList());
        String out = outputFormatter.localVariable(id, lv);
        writer.write(out);
      }
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void formatCode(long id, String path) {
    long startTime = System.nanoTime();
    String name = "Meghanada/formatCode";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      session.formatCode(canonicalPath);
      writer.write(outputFormatter.formatCode(id, canonicalPath));
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void showDeclaration(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/showDeclaration";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Declaration declaration =
          session
              .showDeclaration(path, lineInt, columnInt, symbol)
              .orElse(new Declaration("", "", Declaration.Type.OTHER, 0));
      String out = outputFormatter.showDeclaration(id, declaration);
      writer.write(out);
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void execMain(long id, String path, boolean debug) {
    long startTime = System.nanoTime();
    String name = "Meghanada/execMain";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).put("debug", debug).build("args"));
      InputStream in;
      try {
        in = this.session.execMain(path, debug);
        if (isNull(in)) {
          writer.write("missing main function");
          writer.newLine();
          writer.flush();
          return;
        }
      } catch (Throwable t) {
        TelemetryUtils.ScopedSpan.setStatusINTERNAL(t.getMessage());
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
        span.setStatusOK();
      } catch (Throwable t) {
        TelemetryUtils.ScopedSpan.setStatusINTERNAL(t.getMessage());
        writeError(id, t);
      }

    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void reference(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/reference";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      List<Reference> references = session.reference(path, lineInt, columnInt, symbol);
      String out = outputFormatter.references(id, references);
      writer.write(out);
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void typeInfo(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/typeInfo";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Optional<TypeInfo> typeInfo = session.typeInfo(path, lineInt, columnInt, symbol);
      if (typeInfo.isPresent()) {
        String out = outputFormatter.typeInfo(id, typeInfo.get());
        writer.write(out);
      } else {
        TypeInfo dummy = new TypeInfo("");
        String out = outputFormatter.typeInfo(id, dummy);
        writer.write(out);
      }
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void searchEverywhere(long id, String query) {
    long startTime = System.nanoTime();
    String name = "Meghanada/searchEverywhere";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder().put("query", query).build("args"));
      Optional<SearchResults> results = Session.searchEverywhere(query);
      if (results.isPresent()) {
        String out = outputFormatter.searchEverywhere(id, results.get());
        writer.write(out);
      } else {
        SearchResults dummy = new SearchResults();
        String out = outputFormatter.searchEverywhere(id, dummy);
        writer.write(out);
      }
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void killRunningProcess(long id) {
    long startTime = System.nanoTime();
    String name = "Meghanada/killRunningProcess";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      session.killRunningProcess();
      String out = outputFormatter.killRunningProcess(id);
      writer.write(out);
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void showProject(long id) {
    long startTime = System.nanoTime();
    String name = "Meghanada/showProject";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      String s = session.showProject();
      String out = outputFormatter.showProject(id, s);
      writer.write(out);
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void autocompleteResolve(
      long id, String path, String line, String column, String type, String item, String desc) {
    long startTime = System.nanoTime();
    String name = "Meghanada/autocompleteResolve";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("type", type)
              .put("item", item)
              .put("desc", desc)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      boolean b = session.completionResolve(path, lineInt, columnInt, type, item, desc);
      String out = outputFormatter.completionResolve(id, b);
      writer.write(out);
      writer.newLine();
      writer.flush();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }

  @SuppressWarnings("try")
  public void importAtPoint(long id, String path, String line, String column, String symbol) {
    long startTime = System.nanoTime();
    String name = "Meghanada/importAtPoint";
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan(name);
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      TelemetryUtils.ScopedSpan.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("path", path)
              .put("line", line)
              .put("column", column)
              .put("symbol", symbol)
              .build("args"));
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Map<String, List<String>> result = session.searchImports(path, lineInt, columnInt, symbol);
      String out = outputFormatter.importAtPoint(id, result);
      writer.write(out);
      writer.newLine();
      span.setStatusOK();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    } finally {
      TelemetryUtils.recordCommandLatency(name, TelemetryUtils.sinceInMilliseconds(startTime));
    }
  }
}
