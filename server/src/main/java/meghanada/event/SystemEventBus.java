package meghanada.event;

import static java.util.Objects.isNull;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SystemEventBus {

  private static final Logger log = LogManager.getLogger(SystemEventBus.class);
  private static SystemEventBus systemEventBus;
  private final ExecutorService executorService;
  private final EventBus eventBus;

  private SystemEventBus(int threadNum) {
    this.executorService = Executors.newFixedThreadPool(threadNum);
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
                    shutdown(5);
                  } catch (Throwable t) {
                    log.catching(t);
                  }
                }));
  }

  public static SystemEventBus getInstance() {
    if (isNull(systemEventBus)) {
      systemEventBus = new SystemEventBus(8);
    }
    return systemEventBus;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public EventBus getEventBus() {
    return eventBus;
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
}
