package meghanada.server;

import static java.util.Objects.isNull;

import com.google.common.base.Joiner;
import io.opencensus.common.Scope;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
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
import meghanada.typeinfo.TypeInfo;
import meghanada.utils.ClassNameUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandHandler {

  private static final Logger log = LogManager.getLogger(CommandHandler.class);
  private static final Tracer tracer = Tracing.getTracer();
  private static final EndSpanOptions END_SPAN_OPTIONS =
      EndSpanOptions.builder().setSampleToLocalSpanStore(true).build();

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
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/changeProject", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String canonicalPath = new File(path).getCanonicalPath();
      boolean result = session.changeProject(canonicalPath);
      String out = outputFormatter.changeProject(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void diagnostics(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/diagnostics", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      File f = new File(path);
      String contents = org.apache.commons.io.FileUtils.readFileToString(f);
      CompileResult compileResult = session.diagnosticString(path, contents);
      String out = outputFormatter.diagnostics(id, compileResult, path);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void diagnostics(long id, String sourceFile, String tmpSourceFile) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/diagnostics/2", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      File f = new File(tmpSourceFile);
      f.deleteOnExit();
      try {
        String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
        CompileResult compileResult = session.diagnosticString(sourceFile, contents);
        String out = outputFormatter.diagnostics(id, compileResult, sourceFile);
        writer.write(out);
        writer.newLine();
      } catch (Throwable t) {
        writeError(id, t);
      } finally {
        if (f.exists()) {
          f.delete();
        }
      }
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void compile(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/compile", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      try {
        String canonicalPath = new File(path).getCanonicalPath();
        CompileResult compileResult = session.compileFile(canonicalPath);
        String out = outputFormatter.compile(id, compileResult, canonicalPath);
        writer.write(out);
        writer.newLine();
      } catch (Throwable t) {
        writeError(id, t);
      }
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void compileProject(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/compileProject", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      CompileResult compileResult = session.compileProject(path, true);
      String out = outputFormatter.compileProject(id, compileResult);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void autocomplete(long id, String path, String line, String column, String prefix) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/autocomplete", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Collection<? extends CandidateUnit> units =
          session.completionAt(path, lineInt, columnInt, prefix);
      String out = outputFormatter.autocomplete(id, units);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void runJUnit(long id, String path, String test, boolean debug) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/runJUnit", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
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
      } catch (Throwable t) {
        writeError(id, t);
      }
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void parse(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/parse", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      boolean result = session.parseFile(path);
      String out = outputFormatter.parse(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void addImport(long id, String path, String fqcn) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/addImport", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      boolean result = session.addImport(path, fqcn);
      String out = outputFormatter.addImport(id, result, ClassNameUtils.replaceInnerMark(fqcn));
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void optimizeImport(long id, String sourceFile, String tmpSourceFile) {
    File f = new File(tmpSourceFile);
    f.deleteOnExit();
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/optimizeImport", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String contents = org.apache.commons.io.FileUtils.readFileToString(new File(tmpSourceFile));
      String canonicalPath = f.getCanonicalPath();
      session.optimizeImport(sourceFile, tmpSourceFile, contents);
      writer.write(outputFormatter.optimizeImport(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void importAll(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/importAll", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      Map<String, List<String>> result = session.searchMissingImport(path);
      String out = outputFormatter.importAll(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void switchTest(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/switchTest", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String openPath = session.switchTest(path).orElse(path);
      String out = outputFormatter.switchTest(id, openPath);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void ping(long id) {
    Span span =
        tracer.spanBuilderWithExplicitParent("formatCode", null).setRecordEvents(true).startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String out = outputFormatter.ping(id, "pong");
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void listSymbols(long id, boolean global) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/listSymbols", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String out =
          outputFormatter.listSymbols(
              id, session.listSymbols(global).stream().collect(Collectors.joining("\n")));
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void jumpSymbol(long id, String path, String line, String col, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/jumpSymbol", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(col);

      Location location =
          session
              .jumpSymbol(path, lineInt, columnInt, symbol)
              .orElseGet(() -> new Location(path, lineInt, columnInt));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void jumpDeclaration(long id, String path, String line, String col, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/jumpDeclaration", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(col);

      Location location =
          session
              .jumpDeclaration(path, lineInt, columnInt, symbol)
              .orElseGet(() -> new Location(path, lineInt, columnInt));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void backJump(long id) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/backJump", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      Location location = session.backDeclaration().orElseGet(() -> new Location("", -1, -1));
      String out = outputFormatter.jumpDeclaration(id, location);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void runTask(long id, List<String> args) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/runTask", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span);
        InputStream in = this.session.runTask(args)) {
      String tasks = Joiner.on(" ").join(args);
      writer.write("run task: ");
      writer.write(tasks);
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
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void clearCache(long id) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/clearCache", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      boolean result = this.session.clearCache();
      String out = outputFormatter.clearCache(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void localVariable(long id, String path, String line) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/localVariable", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
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
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void formatCode(long id, String path) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/formatCode", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {

      String canonicalPath = new File(path).getCanonicalPath();
      session.formatCode(canonicalPath);
      writer.write(outputFormatter.formatCode(id, canonicalPath));
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void showDeclaration(long id, String path, String line, String col, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/showDeclaration", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(col);
      Declaration declaration =
          session
              .showDeclaration(path, lineInt, columnInt, symbol)
              .orElse(new Declaration("", "", Declaration.Type.OTHER, 0));
      String out = outputFormatter.showDeclaration(id, declaration);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void execMain(long id, String path, boolean debug) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/execMain", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
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
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void reference(long id, String path, String line, String col, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("showDeclaration", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(col);
      List<Reference> references = session.reference(path, lineInt, columnInt, symbol);
      String out = outputFormatter.references(id, references);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void typeInfo(long id, String path, String line, String col, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/typeInfo", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(col);
      try {
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
        writeError(id, t);
      }
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void searchEverywhere(long id, String q) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/searchEverywhere", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      Optional<SearchResults> results = Session.searchEverywhere(q);
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
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void killRunningProcess(long id) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/killRunningProcess", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      session.killRunningProcess();
      String out = outputFormatter.killRunningProcess(id);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void showProject(long id) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/showProject", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      String s = session.showProject();
      String out = outputFormatter.showProject(id, s);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void autocompleteResolve(
      long id, String path, String line, String column, String type, String item, String desc) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/autocompleteResolve", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      boolean b = session.completionResolve(path, lineInt, columnInt, type, item, desc);
      String out = outputFormatter.completionResolve(id, b);
      writer.write(out);
      writer.newLine();
      writer.flush();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }

  public void importAtPoint(long id, String path, String line, String column, String symbol) {
    Span span =
        tracer
            .spanBuilderWithExplicitParent("Meghanada/importAtPoint", null)
            .setRecordEvents(true)
            .startSpan();
    try (Scope ss = tracer.withSpan(span)) {
      int lineInt = Integer.parseInt(line);
      int columnInt = Integer.parseInt(column);
      Map<String, List<String>> result = session.searchImports(path, lineInt, columnInt, symbol);
      String out = outputFormatter.importAtPoint(id, result);
      writer.write(out);
      writer.newLine();
    } catch (Throwable t) {
      writeError(id, t);
    } finally {
      span.end(END_SPAN_OPTIONS);
    }
  }
}
