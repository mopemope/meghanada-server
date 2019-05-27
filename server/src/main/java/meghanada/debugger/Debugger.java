package meghanada.debugger;

import static java.util.Objects.nonNull;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Debugger {

  private static final Logger log = LogManager.getLogger(Debugger.class);
  private static final String KEY_MEGHANADA_BREAKPOINT = "meghanada-breakpoint";

  private final EventBus eventBus;
  private final ExecutorService executorService;

  private VirtualMachine vm;
  private boolean start;
  private EventRequestManager eventRequestManager;
  private BreakpointEvent currentBreakpoint;

  public Debugger(Class clazz) throws Exception {
    this.eventBus = new EventBus("meghanada-debugger");
    this.eventBus.register(this);
    this.executorService = Executors.newCachedThreadPool();
    this.launch(clazz);
  }

  private void launch(Class clazz) throws Exception {
    VirtualMachineManager virtualMachineManager = Bootstrap.virtualMachineManager();
    LaunchingConnector launchingConnector = virtualMachineManager.defaultConnector();
    Map<String, Connector.Argument> map = launchingConnector.defaultArguments();
    map.get("main").setValue(clazz.getCanonicalName());
    // TODO add option and args
    String cp = System.getProperty("java.class.path");
    map.get("options").setValue("-classpath \"" + cp + "\"");
    VirtualMachine virtualMachine = launchingConnector.launch(map);
    this.vm = virtualMachine;
    this.eventRequestManager = vm.eventRequestManager();
  }

  public void addBreakpoint(String clazz, int line) {
    ClassPrepareRequest request = this.eventRequestManager.createClassPrepareRequest();
    request.addClassFilter(clazz);
    request.putProperty(KEY_MEGHANADA_BREAKPOINT, line);
    request.enable();
  }

  public void addBreakpoint(File file, int line) throws IOException {
    ClassPrepareRequest request = this.eventRequestManager.createClassPrepareRequest();
    request.addSourceNameFilter(file.getCanonicalPath());
    request.putProperty(KEY_MEGHANADA_BREAKPOINT, line);
    request.enable();
  }

  public void start() throws InterruptedException, ExecutionException {
    this.start = true;
    this.executorService.execute(this::startDebug);
    this.executorService.execute(
        () -> {
          final byte[] buf = new byte[4096];
          int ret;
          try (InputStream in = this.vm.process().getInputStream()) {
            while ((ret = in.read(buf)) != -1) {
              System.out.write(buf, 0, ret);
            }
          } catch (IOException e) {
            log.catching(e);
          }
        });
  }

  public void stopLoop() {
    this.start = false;
  }

  private void startDebug() {
    EventQueue queue = this.vm.eventQueue();
    while (this.start) {
      try {
        EventSet eventSet = queue.remove();
        EventIterator it = eventSet.eventIterator();
        while (it.hasNext()) {
          Event event = it.nextEvent();
          this.eventBus.post(event);
        }
        eventSet.resume();
      } catch (Throwable t) {
        this.start = false;
        this.vm.dispose();
      }
    }
    this.shutdownVM();
  }

  private void shutdownVM() {
    if (nonNull(this.vm)) {
      try {
        this.vm.dispose();
      } catch (VMDisconnectedException ex) {
        // ignore
      }
    }
  }

  public BreakpointEvent getCurrentBreakpoint() {
    return currentBreakpoint;
  }

  public void suspend() {
    if (nonNull(this.vm)) {
      this.vm.suspend();
    }
  }

  public void resume() {
    if (nonNull(this.vm)) {
      this.vm.resume();
    }
  }

  private void stop() {
    this.executorService.shutdownNow();
  }

  @Subscribe
  private void handleEvent(VMStartEvent event) {
    log.info("{}", event.getClass().getCanonicalName());
  }

  @Subscribe
  private void handleEvent(VMDeathEvent event) {
    this.start = false;
    log.info("{}", event.getClass().getCanonicalName());
  }

  @Subscribe
  private void handleEvent(VMDisconnectEvent event) {
    this.start = false;
    log.info("{}", event.getClass().getCanonicalName());
    this.shutdownVM();
  }

  @Subscribe
  private void handleEvent(ClassPrepareEvent event) throws AbsentInformationException {
    EventRequest request = event.request();
    if (nonNull(request)) {
      ClassType classType = (ClassType) event.referenceType();
      int line = (int) request.getProperty(KEY_MEGHANADA_BREAKPOINT);
      List<Location> locations = classType.locationsOfLine(line);
      if (!locations.isEmpty()) {
        Location location = locations.get(0);
        BreakpointRequest breakpointRequest =
            this.eventRequestManager.createBreakpointRequest(location);
        breakpointRequest.enable();
        log.info("set breakpoint {}#L{}", location.sourcePath(), location.lineNumber());
      }
    }
  }

  @Subscribe
  private void handleEvent(BreakpointEvent event) throws Exception {
    // event.disable();
    this.currentBreakpoint = event;

    ThreadReference thread = this.currentBreakpoint.thread();
    StackFrame stackFrame = thread.frame(0);

    Map<LocalVariable, Value> visibleVariables =
        stackFrame.getValues(stackFrame.visibleVariables());
    for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
      System.out.println(entry.getKey() + ":" + entry.getValue());
    }

    event.virtualMachine().suspend();
  }

  public static void main(String[] args) throws Exception {
    Debugger debugger = new Debugger(TestMain.class);
    debugger.addBreakpoint("meghanada.debugger.TestMain", 7);
    debugger.start();
    Thread.sleep(1000);
    debugger.resume();
    Thread.sleep(1000);
    debugger.stop();
    log.info("finish");
  }
}
