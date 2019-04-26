package meghanada.session.subscribe;

import static sun.management.ManagementFactoryHelper.getOperatingSystemMXBean;

import com.google.common.eventbus.Subscribe;
import com.sun.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import meghanada.system.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IdleMonitorSubscriber extends AbstractSubscriber {

  private static final Logger log = LogManager.getLogger(IdleMonitorSubscriber.class);
  private static final double CPU_LIMIT = 0.1;
  private static final long IDLE_CHECK_INTERVAL = 1000;
  private static final long WARMUP_INTERVAL = 15000;

  private int idleTime = 10;
  private Set<String> queue = Collections.synchronizedSet(new LinkedHashSet(32));
  private final SessionEventBus.IdleTimer idleTimer;
  private boolean started;
  private final CpuMonitor monitor;

  public IdleMonitorSubscriber(
      SessionEventBus sessionEventBus, SessionEventBus.IdleTimer idleTimer) {
    super(sessionEventBus);
    this.idleTimer = idleTimer;
    this.monitor = new CpuMonitor();
  }

  private static SessionEventBus.IdleEvent createIdleEvent(
      Session session, SessionEventBus.IdleTimer idleTimer) {
    return new SessionEventBus.IdleEvent(session, idleTimer);
  }

  @Subscribe
  public void on(final SessionEventBus.IdleMonitorEvent event) {
    if (this.started) {
      return;
    }
    this.started = true;
    try {
      Thread.sleep(WARMUP_INTERVAL);
    } catch (InterruptedException e) {
      log.catching(e);
      throw new RuntimeException(e);
    }
    Config config = Config.load();
    this.idleTime = config.getIdleCacheInterval();
    if (this.idleTime < 1) {
      this.idleTime = 1;
    }
    while (this.started) {
      try {
        Thread.sleep(IDLE_CHECK_INTERVAL);
        long now = Instant.now().getEpochSecond();
        if (this.isIdle(now)) {
          SessionEventBus.IdleEvent idleEvent = createIdleEvent(event.session, this.idleTimer);
          Executor.getInstance().getEventBus().post(idleEvent);
          this.idleTimer.lastRun = now + this.idleTime + 1;
        }
      } catch (InterruptedException e) {
        log.catching(e);
      }
    }
  }

  private boolean isIdle(long now) {
    return (now - this.idleTimer.lastRun) > this.idleTime && this.monitor.getCpuUsage() < CPU_LIMIT;
  }

  @Subscribe
  public synchronized void on(SessionEventBus.IdleEvent event) {
    Iterator<String> it = this.queue.iterator();
    int cnt = 20;
    while (cnt-- > 0) {
      if (it.hasNext()) {
        String name = it.next();
        try {
          GlobalCache.getInstance().getMemberDescriptors(name);
        } catch (ExecutionException e) {
          log.catching(e);
        } finally {
          try {
            it.remove();
          } catch (Throwable e) {
            //
          }
        }
      }
    }
  }

  @Subscribe
  public void on(SessionEventBus.IdleCacheEvent event) {
    this.queue.addAll(event.getNames());
  }

  private static class CpuMonitor {
    private int availableProcessors = getOperatingSystemMXBean().getAvailableProcessors();
    private long lastSystemTime = 0;
    private long lastProcessCpuTime = 0;

    public synchronized double getCpuUsage() {
      if (this.lastSystemTime == 0) {
        this.baselineCounters();
        return 0;
      }

      long systemTime = System.nanoTime();
      long processCpuTime = 0;

      if (getOperatingSystemMXBean() instanceof OperatingSystemMXBean) {
        processCpuTime = ((OperatingSystemMXBean) getOperatingSystemMXBean()).getProcessCpuTime();
      }

      double cpuUsage =
          ((double) (processCpuTime - this.lastProcessCpuTime))
              / ((double) (systemTime - this.lastSystemTime));
      this.lastSystemTime = systemTime;
      this.lastProcessCpuTime = processCpuTime;
      return cpuUsage;
    }

    private void baselineCounters() {
      this.lastSystemTime = System.nanoTime();

      if (getOperatingSystemMXBean() instanceof OperatingSystemMXBean) {
        this.lastProcessCpuTime =
            ((OperatingSystemMXBean) getOperatingSystemMXBean()).getProcessCpuTime();
      }
    }
  }
}
