package meghanada.debugger;

import com.sun.jdi.event.Event;

public interface DebugEventListener {

  void onEvent(Event event);
}
