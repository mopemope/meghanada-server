package meghanada.server.emacs;

import static com.leacox.motif.MatchesAny.any;
import static com.leacox.motif.MatchesExact.eq;
import static com.leacox.motif.Motif.match;
import static com.leacox.motif.cases.ListConsCases.headNil;
import static com.leacox.motif.cases.ListConsCases.headTail;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Stopwatch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import meghanada.server.CommandHandler;
import meghanada.server.OutputFormatter;
import meghanada.server.Server;
import meghanada.server.formatter.SexpOutputFormatter;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EmacsServer implements Server {

  private static final Logger log = LogManager.getLogger(EmacsServer.class);
  private static final String EOT = ";;EOT";
  private final ServerSocket serverSocket;
  private final ExecutorService executorService = Executors.newFixedThreadPool(5);
  private final OUTPUT outputFormat;
  private final String projectRoot;
  private final String host;
  private final int port;
  private final boolean outputEOT;
  private SessionEventBus.IdleTimer idleTimer;

  private Session session;
  private long id;

  public EmacsServer(final String host, final int port, final String projectRoot)
      throws IOException {
    final InetAddress address = InetAddress.getByName(host);
    this.host = host;
    this.port = port;
    this.serverSocket = new ServerSocket(port, 0, address);
    this.projectRoot = projectRoot;
    this.outputFormat = OUTPUT.SEXP;
    this.outputEOT = true;
    System.setProperty("meghanada.server.port", Integer.toString(this.serverSocket.getLocalPort()));
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    shutdown();
                  } catch (Throwable t) {
                    log.catching(t);
                  }
                }));
  }

  private boolean dispatch(final List<String> argList, final CommandHandler handler) {
    if (nonNull(this.idleTimer)) {
      this.idleTimer.lastRun = Instant.now().getEpochSecond();
    }
    final Stopwatch stopwatch = Stopwatch.createStarted();
    id++;
    final boolean result =
        match(argList)
            .when(headTail(eq("pc"), any()))
            .get(
                args -> {
                  // pc : Project Change
                  // usage: pc <filepath>
                  handler.changeProject(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("p"), any()))
            .get(
                args -> {
                  // p : Parse
                  // usage: p <filepath>
                  handler.parse(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("ap"), any()))
            .get(
                args -> {
                  // ap : Autocomplete Prefix
                  // usage: ap <filepath> <line> <column> <prefix> <fmt>
                  handler.autocomplete(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("cr"), any()))
            .get(
                args -> {
                  // cr : CompletionItem resolve
                  // usage: cr <filepath> <line> <column> <type> <item> <desc>
                  handler.autocompleteResolve(
                      id,
                      args.get(0),
                      args.get(1),
                      args.get(2),
                      args.get(3),
                      args.get(4),
                      args.get(5));
                  return true;
                })
            .when(headTail(eq("c"), any()))
            .get(
                args -> {
                  // c : Compile
                  // usage: c <filepath>
                  handler.compile(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("cp"), any()))
            .get(
                args -> {
                  // cp : Compile Project
                  // usage: cp <filepath>
                  handler.compileProject(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("fc"), any()))
            .get(
                args -> {
                  // fc : Format code
                  // usage: fc <filepath>
                  handler.formatCode(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("di"), any()))
            .get(
                args -> {
                  // di : Diagnostic
                  // usage: di <filepath>
                  handler.diagnostics(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("dl"), any()))
            .get(
                args -> {
                  // dl : Diagnostic live
                  // usage: dl <filepath> <contents-file>
                  handler.diagnostics(id, args.get(0), args.get(1));
                  return true;
                })
            .when(headTail(eq("rj"), any()))
            .get(
                args -> {
                  // rj : Run JUnit Test
                  // usage: rj <path>, <testName>
                  if (args.size() == 1) {
                    handler.runJUnit(id, args.get(0), "", false);
                  } else {
                    handler.runJUnit(id, args.get(0), args.get(1), false);
                  }
                  return true;
                })
            .when(headTail(eq("dj"), any()))
            .get(
                args -> {
                  // dj : Debug JUnit Test
                  // usage: dj <path>, <testName>
                  if (args.size() == 1) {
                    handler.runJUnit(id, args.get(0), "", true);
                  } else {
                    handler.runJUnit(id, args.get(0), args.get(1), true);
                  }
                  return true;
                })
            .when(headTail(eq("rt"), any()))
            .get(
                args -> {
                  // rj : Run Task
                  // usage: rt <args>
                  handler.runTask(id, args);
                  return true;
                })
            .when(headTail(eq("ai"), any()))
            .get(
                args -> {
                  // ai : Add Import
                  // usage: ai <filepath> <import>
                  handler.addImport(id, args.get(0), args.get(1));
                  return true;
                })
            .when(headTail(eq("oi"), any()))
            .get(
                args -> {
                  // oi : Optimize Import
                  // usage: oi <filepath> <contents-file>
                  handler.optimizeImport(id, args.get(0), args.get(1));
                  return true;
                })
            .when(headTail(eq("ia"), any()))
            .get(
                args -> {
                  // ia : Import All
                  // usage: ia <filepath>
                  handler.importAll(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("ip"), any()))
            .get(
                args -> {
                  // ip : Import at point
                  // usage: ip <filepath> <line> <column> <symbol>
                  handler.importAtPoint(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("st"), any()))
            .get(
                args -> {
                  // st : Switch test to src or src to test
                  // usage: st <filepath>
                  handler.switchTest(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("jd"), any()))
            .get(
                args -> {
                  // jd : Jump Declaration
                  // usage: jd <filepath> <line> <column> <symbol>
                  handler.jumpDeclaration(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("sd"), any()))
            .get(
                args -> {
                  // sd : Show declaration (short)
                  // usage: sd <filepath> <line> <column> <symbol>
                  handler.showDeclaration(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("re"), any()))
            .get(
                args -> {
                  // re : References
                  // usage: re <filepath> <line> <column> <symbol>
                  handler.reference(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("ti"), any()))
            .get(
                args -> {
                  // ti : TypeInfo (short)
                  // usage: ti <filepath> <line> <column> <symbol>
                  handler.typeInfo(id, args.get(0), args.get(1), args.get(2), args.get(3));
                  return true;
                })
            .when(headTail(eq("bj"), any()))
            .get(
                args -> {
                  // bj : Back Jump
                  // usage: bj
                  handler.backJump(id);
                  return true;
                })
            .when(headTail(eq("cc"), any()))
            .get(
                args -> {
                  // cc : Clear cache
                  // usage: cc
                  handler.clearCache(id);
                  return true;
                })
            .when(headTail(eq("lv"), any()))
            .get(
                args -> {
                  // lv : Local variable
                  // usage: lv file line
                  handler.localVariable(id, args.get(0), args.get(1));
                  return true;
                })
            .when(headTail(eq("ping"), any()))
            .get(
                args -> {
                  // st : Switch test to src or src to test
                  // usage: st <filepath>
                  handler.ping(id);
                  return true;
                })
            .when(headTail(eq("kp"), any()))
            .get(
                args -> {
                  // kp : Kill running process
                  // usage: kp
                  handler.killRunningProcess(id);
                  return true;
                })
            .when(headTail(eq("em"), any()))
            .get(
                args -> {
                  // em : Exec main
                  // usage: em <path>
                  handler.execMain(id, args.get(0), false);
                  return true;
                })
            .when(headTail(eq("dm"), any()))
            .get(
                args -> {
                  // dm : Debug main
                  // usage: dm <path>
                  handler.execMain(id, args.get(0), true);
                  return true;
                })
            .when(headTail(eq("se"), any()))
            .get(
                args -> {
                  // se : Search Everywhere
                  // usage: se <keyword>
                  handler.searchEverywhere(id, args.get(0));
                  return true;
                })
            .when(headTail(eq("sp"), any()))
            .get(
                args -> {
                  // sp : Show project
                  // usage: sp
                  handler.showProject(id);
                  return true;
                })
            .when(headNil(eq("q")))
            .get(
                () -> {
                  // q : Quit session
                  // usage: q
                  return false;
                })
            .getMatch();

    log.info("receive command {}. elapsed:{}", argList, stopwatch.stop());
    return result;
  }

  @Override
  public void startServer() throws IOException {
    if (this.executorService.isShutdown()) {
      return;
    }
    if (this.executorService.isTerminated()) {
      return;
    }

    try {
      this.session = Session.createSession(projectRoot);
      this.session.start();
      log.info("Start server Listen {} port:{}", this.host, this.serverSocket.getLocalPort());
      this.idleTimer = this.session.getSessionEventBus().getIdleTimer();
      this.accept();
    } catch (Throwable e) {
      log.catching(e);
    } finally {
      try {
        this.serverSocket.close();
        this.executorService.shutdownNow();
        if (nonNull(this.session)) {
          this.session.shutdown(3);
        }
      } catch (Exception e) {
        log.catching(e);
      }
    }
  }

  private void accept() throws IOException {
    List<Future<?>> futures = new ArrayList<>(2);
    while (!this.serverSocket.isClosed()) {
      final Socket conn = this.serverSocket.accept();
      log.info("client connected");
      conn.setKeepAlive(true);
      final Future<?> future = acceptConnection(conn);
      futures.add(future);
    }

    futures.forEach(
        (future) -> {
          try {
            future.get();
          } catch (InterruptedException | ExecutionException e) {
            log.catching(e);
          }
        });
  }

  private Future<?> acceptConnection(final Socket conn) {

    return this.executorService.submit(
        () -> {
          try (final BufferedReader reader =
                  new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8));
              final BufferedWriter writer =
                  new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), UTF_8))) {

            final CommandHandler handler =
                new CommandHandler(session, writer, getOutputFormatter());
            boolean start = true;
            final SExprParser parser = new SExprParser();
            while (start) {
              final String line = reader.readLine();
              if (isNull(line) || line.isEmpty()) {
                log.info("close from client ...");
                break;
              }
              final SExprParser.SExpr expr = parser.parse(line);
              final List<SExprParser.SExpr> lst = expr.value();
              final List<String> args =
                  lst.stream().map(sExpr -> sExpr.value().toString()).collect(Collectors.toList());

              log.debug("receive command line:{} expr:{} args:{}", line, expr, args);
              start = dispatch(args, handler);
              if (!start) {
                log.info("stop client ... args:{}", args);
              }
              if (this.outputEOT) {
                writer.write(EmacsServer.EOT);
                writer.newLine();
              }

              writer.flush();
            }
            log.info("close client ...");
          } catch (Throwable e) {
            log.catching(e);
          } finally {
            try {
              conn.close();
            } catch (IOException e) {
              log.catching(e);
            }
            log.info("client disconnect");
          }
        });
  }

  private OutputFormatter getOutputFormatter() {
    if (this.outputFormat == OUTPUT.SEXP) {
      return new SexpOutputFormatter();
    }
    throw new UnsupportedOperationException("not support format");
  }

  public void shutdown() {
    this.executorService.shutdown();
    try {
      this.executorService.awaitTermination(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.catching(e);
    }
  }

  private enum OUTPUT {
    SEXP,
    CSV,
    JSON,
  }
}
