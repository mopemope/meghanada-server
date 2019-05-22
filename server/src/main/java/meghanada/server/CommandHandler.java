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
import java.util.stream.Collectors;
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

  public void changeProject(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/changeProject");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      boolean result = session.changeProject(canonicalPath);
      String out = outputFormatter.changeProject(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void diagnostics(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/diagnostics");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      File f = new File(path);
      String contents = org.apache.commons.io.FileUtils.readFileToString(f);
      CompileResult compileResult = session.diagnosticString(path, contents);
      String out = outputFormatter.diagnostics(id, compileResult, path);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void diagnostics(long id, String sourceFile, String tmpSourceFile) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/diagnostics/2");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
      } finally {
        if (f.exists()) {
          f.delete();
        }
      }
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void compile(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/compile");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      CompileResult compileResult = session.compileFile(canonicalPath);
      String out = outputFormatter.compile(id, compileResult, canonicalPath);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void compileProject(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/compileProject");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      CompileResult compileResult = session.compileProject(path, true);
      String out = outputFormatter.compileProject(id, compileResult);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void autocomplete(long id, String path, String line, String column, String prefix) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/autocomplete");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void runJUnit(long id, String path, String test, boolean debug) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/runJUnit");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void parse(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/parse");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      boolean result = session.parseFile(path);
      String out = outputFormatter.parse(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void addImport(long id, String path, String fqcn) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/addImport");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
          TelemetryUtils.annotationBuilder().put("path", path).put("fqcn", fqcn).build("args"));
      boolean result = session.addImport(path, fqcn);
      String out = outputFormatter.addImport(id, result, ClassNameUtils.replaceInnerMark(fqcn));
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void optimizeImport(long id, String sourceFile, String tmpSourceFile) {
    File f = new File(tmpSourceFile);
    f.deleteOnExit();
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/optimizeImport");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
          TelemetryUtils.annotationBuilder()
              .put("sourceFile", sourceFile)
              .put("tmpSourceFile", tmpSourceFile)
              .build("args"));
      String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
      String canonicalPath = f.getCanonicalPath();
      session.optimizeImport(sourceFile, tmpSourceFile, contents);
      writer.write(outputFormatter.optimizeImport(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void importAll(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/importAll");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      Map<String, List<String>> result = session.searchMissingImport(path);
      String out = outputFormatter.importAll(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void switchTest(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/switchTest");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String openPath = session.switchTest(path).orElse(path);
      String out = outputFormatter.switchTest(id, openPath);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void ping(long id) {
    try (TelemetryUtils.ParentSpan span = TelemetryUtils.startExplicitParentSpan("Meghanada/ping");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      String out = outputFormatter.ping(id, "pong");
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void listSymbols(long id, boolean global) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/listSymbols");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("global", global).build("args"));
      String out =
          outputFormatter.listSymbols(
              id, session.listSymbols(global).stream().collect(Collectors.joining("\n")));
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void jumpSymbol(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/jumpSymbol");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void jumpDeclaration(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/jumpDeclaration");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void backJump(long id) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/backJump");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      Location location = session.backDeclaration().orElseGet(() -> new Location("", -1, -1));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void runTask(long id, List<String> tasks) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/runTask");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan());
        InputStream in = this.session.runTask(tasks)) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void clearCache(long id) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/clearCache");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      boolean result = this.session.clearCache();
      String out = outputFormatter.clearCache(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void localVariable(long id, String path, String line) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/localVariable");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("line", line).build("args"));
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void formatCode(long id, String path) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/formatCode");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("path", path).build("args"));
      String canonicalPath = new File(path).getCanonicalPath();
      session.formatCode(canonicalPath);
      writer.write(outputFormatter.formatCode(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void showDeclaration(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/showDeclaration");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void execMain(long id, String path, boolean debug) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/execMain");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
        scope.setStatusINTERNAL(t.getMessage());
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
        scope.setStatusINTERNAL(t.getMessage());
        writeError(id, t);
      }

    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void reference(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/reference");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void typeInfo(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/typeInfo");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void searchEverywhere(long id, String query) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/searchEverywhere");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("query", query).build("args"));
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void killRunningProcess(long id) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/killRunningProcess");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      session.killRunningProcess();
      String out = outputFormatter.killRunningProcess(id);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void showProject(long id) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/showProject");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(TelemetryUtils.annotationBuilder().build("args"));
      String s = session.showProject();
      String out = outputFormatter.showProject(id, s);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void autocompleteResolve(
      long id, String path, String line, String column, String type, String item, String desc) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/autocompleteResolve");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }

  public void importAtPoint(long id, String path, String line, String column, String symbol) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("Meghanada/importAtPoint");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      scope.addAnnotation(
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
    } catch (Throwable t) {
      TelemetryUtils.setStatusINTERNAL(t.getMessage());
      writeError(id, t);
    }
  }
}
