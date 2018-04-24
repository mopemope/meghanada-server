package meghanada.server.emacs;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import meghanada.cache.GlobalCache;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IdleCacheSubscriber {

  private static final Logger log = LogManager.getLogger(IdleCacheSubscriber.class);

  private Deque<File> jars;

  public IdleCacheSubscriber() {}

  @Subscribe
  public void on(EmacsServer.IdleEvent event) {
    long preLastRun = event.getIdleTimer().lastRun;
    CachedASMReflector reflector = CachedASMReflector.getInstance();
    if (isNull(this.jars)) {
      this.jars = new ArrayDeque<>(reflector.getJars());
    }

    // max 5 file
    int i = 5;
    while (i-- > 0) {
      File file = jars.pollFirst();
      if (nonNull(file)) {
        reflector.scan(
            file,
            name -> {
              try {
                List<MemberDescriptor> memberDescriptors =
                    GlobalCache.getInstance().getMemberDescriptors(name);
              } catch (ExecutionException e) {
                log.catching(e);
                throw new RuntimeException(e);
              }
            });
        long lastRun = event.getIdleTimer().lastRun;
        if (preLastRun != lastRun) {
          return;
        }
      }
    }
  }
}
