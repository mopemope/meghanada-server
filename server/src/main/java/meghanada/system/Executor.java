package meghanada.system;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Throwables;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Executor {

  private static final Logger log = LogManager.getLogger(Executor.class);

  private static Executor executor;
  private final ExecutorService executorService;
  private final ExecutorService fixedThreadPool;
  private final EventBus eventBus;

  private Executor() {
    int size = Runtime.getRuntime().availableProcessors() * 2;
    this.executorService = Executors.newCachedThreadPool();
    this.fixedThreadPool = Executors.newFixedThreadPool(size);
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

  public ExecutorService getCachedExecutorService() {
    return executorService;
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  public <U> CompletableFuture<U> runIOAction(Supplier<U> supplier) {
    return CompletableFuture.supplyAsync(supplier, fixedThreadPool);
  }

  public CompletableFuture<Void> runIOAction(Runnable runnable) {
    return CompletableFuture.runAsync(runnable, fixedThreadPool);
  }

  private void shutdown(int timeout) {
    if (executorService.isShutdown()) {
      return;
    }
    if (fixedThreadPool.isShutdown()) {
      return;
    }
    try {
      executorService.shutdown();
      fixedThreadPool.shutdown();
      if (!executorService.awaitTermination(timeout, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
        executorService.awaitTermination(timeout, TimeUnit.SECONDS);
      }
      if (!fixedThreadPool.awaitTermination(timeout, TimeUnit.SECONDS)) {
        fixedThreadPool.shutdownNow();
        fixedThreadPool.awaitTermination(timeout, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      fixedThreadPool.shutdownNow();
      try {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        // ignore
      }
    }
  }

  public <T> CompletableFutures<T> completableFutures(int cap) {
    return new Executor.CompletableFutures<T>(cap);
  }

  public void execute(Runnable runnable) {
    this.fixedThreadPool.execute(runnable);
  }

  public static class CompletableFutures<T> {
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
          CompletableFuture.supplyAsync(supplier, Executor.getInstance().fixedThreadPool);
      this.cfs.add(f);
    }

    public CompletableFuture<Void> whenComplete(Consumer<List<CompletableFuture<T>>> consumer) {
      return CompletableFuture.allOf(this.cfs.toArray(COMPLETABLE_FUTURES))
          .whenComplete(
              (res, ex) -> {
                if (nonNull(ex)) {
                  log.catching(ex);
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
