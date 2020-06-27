package meghanada.system;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Throwables;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import meghanada.telemetry.ErrorReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Executor {

  private static final Logger log = LogManager.getLogger(Executor.class);

  private static Executor executor;
  private final ExecutorService executorService;
  private final EventBus eventBus;

  private Executor() {
    this.executorService = getExecutorService();
    this.eventBus =
        new AsyncEventBus(
            executorService,
            (throwable, subscriberExceptionContext) -> {
              if (!(throwable instanceof RejectedExecutionException)) {
                log.catching(throwable);
              }
            });
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    shutdown(1);
                  } catch (Throwable t) {
                    // log.catching(t);
                  }
                }));
  }

  public static Executor getInstance() {
    if (isNull(executor)) {
      executor = new Executor();
    }
    return executor;
  }

  private static ExecutorService getExecutorService() {
    int processors = Runtime.getRuntime().availableProcessors();
    return new ThreadPoolExecutor(
        processors + 2,
        processors * 50,
        60L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16),
        new ThreadFactoryBuilder().setNameFormat("Meghanada Thread Pool %d").build());
  }

  public ExecutorService getCachedExecutorService() {
    return executorService;
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  public <U> CompletableFuture<U> runIOAction(Supplier<U> supplier) {
    return CompletableFuture.supplyAsync(supplier, executorService);
  }

  public CompletableFuture<Void> runIOAction(Runnable runnable) {
    return CompletableFuture.runAsync(runnable, executorService);
  }

  private void shutdown(int timeout) {
    if (executorService.isShutdown()) {
      return;
    }
    try {
      executorService.shutdown();
      if (!executorService.awaitTermination(timeout, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
        executorService.awaitTermination(timeout, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      try {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        // ignore
      }
    }
  }

  public static <T> CompletableFutures<T> completableFutures(int cap) {
    return new Executor.CompletableFutures<>(cap);
  }

  public void execute(Runnable runnable) {
    this.executorService.execute(runnable);
  }

  public static class CompletableFutures<T> {
    @SuppressWarnings("rawtypes")
    static final CompletableFuture[] COMPLETABLE_FUTURES = new CompletableFuture[0];

    private final List<CompletableFuture<T>> cfs;

    CompletableFutures() {
      this(8);
    }

    CompletableFutures(int init) {
      this.cfs = new ArrayList<>(init);
    }

    public void runIOAction(Supplier<T> supplier) {
      CompletableFuture<T> f =
          CompletableFuture.supplyAsync(supplier, Executor.getInstance().executorService);
      this.cfs.add(f);
    }

    public CompletableFuture<Void> whenComplete(Consumer<List<CompletableFuture<T>>> consumer) {
      return CompletableFuture.allOf(this.cfs.toArray(COMPLETABLE_FUTURES))
          .whenComplete(
              (res, ex) -> {
                if (nonNull(ex)) {
                  log.catching(ex);
                  ErrorReporter.report(ex);
                  Throwables.throwIfUnchecked(ex);
                  throw new RuntimeException(ex);
                } else {
                  consumer.accept(this.cfs);
                }
              });
    }

    public <U> CompletableFuture<U> thenApply(
        Function<List<CompletableFuture<T>>, ? extends U> fn) {
      return CompletableFuture.allOf(this.cfs.toArray(COMPLETABLE_FUTURES))
          .thenApply(v -> fn.apply(this.cfs));
    }
  }
}
