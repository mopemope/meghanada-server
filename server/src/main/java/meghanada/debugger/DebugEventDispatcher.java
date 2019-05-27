package meghanada.debugger;

import static java.util.Objects.nonNull;

import com.sun.jdi.event.Event;
import com.sun.jdi.request.EventRequest;
import java.util.List;

public class DebugEventDispatcher {

  private List<DebugEventListener> listeners;

  public DebugEventDispatcher() {}

  public void dispatch(Event event) {
    EventRequest req = event.request();
    if (nonNull(req)) {
      // no request
      listeners.forEach(
          listener -> {
            listener.onEvent(event);
          });
    } else {
      // request

    }
  }
}
