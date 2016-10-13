package meghanada.junit;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestRunner {

    private static Logger log = LogManager.getLogger(TestRunner.class);
    private int runCnt;
    private int failureCnt;
    private int ignoreCnt;

    public TestRunner() throws IOException {
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(new File("./"));
        cachedASMReflector.createClassIndexes();
    }

    public static void main(String... args) throws IOException, ClassNotFoundException {
        new TestRunner().runTests(args);
    }

    private List<Class<?>> getTestClass(String testName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        for (ClassIndex classIndex : cachedASMReflector.getGlobalClassIndex().values()) {
            String fqcn = classIndex.getReturnType();
            String className = classIndex.getName();

            if (fqcn.equals(testName)
                    || className.equals(testName)
                    || fqcn.matches(testName)) {
                classes.add(Class.forName(fqcn));
            }
        }
        return classes;
    }

    public void runTests(String... args) throws ClassNotFoundException {

        for (String arg : args) {
            List<Request> requests = collectTests(arg);
            for (Request request : requests) {
                this.runJunit(arg, request);
            }
        }
        System.exit(0);
    }

    private void runJunit(String arg, Request request) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        System.out.println(String.format("Running %s", arg));
        System.out.println("");
        JUnitCore jUnitCore = new JUnitCore();
        Result result = jUnitCore.run(request);

        this.runCnt += result.getRunCount();
        this.failureCnt += result.getFailureCount();
        this.ignoreCnt += result.getIgnoreCount();
        System.out.println("");

        if (result.getFailureCount() > 0) {
            System.out.println(String.format("FAIL Tests run: %d, Failures: %d, Ignore: %d, Time elapsed: %s",
                    result.getRunCount(),
                    result.getFailureCount(),
                    result.getIgnoreCount(),
                    stopwatch.stop()));
            System.out.println("Failures:");
            for (Failure failure : result.getFailures()) {
                System.out.println(failure.getDescription());
                failure.getException().printStackTrace();
                System.out.println("");
            }
        } else {
            System.out.println(String.format("Tests run: %d, Failures: %d, Ignore: %d, Time elapsed: %s",
                    result.getRunCount(),
                    result.getFailureCount(),
                    result.getIgnoreCount(),
                    stopwatch.stop()));
            System.out.println("Success");
        }
        System.out.println(Strings.repeat("-", 80));
    }

    private List<Request> collectTests(String arg) throws ClassNotFoundException {
        List<Request> requests = new ArrayList<>(1);

        if (arg.contains("#")) {
            String[] classAndMethod = arg.split("#");
            List<Class<?>> classes = getTestClass(classAndMethod[0]);
            for (Class<?> cls : classes) {
                Request request = Request.method(cls, classAndMethod[1]);
                requests.add(request);
            }
        } else {
            List<Class<?>> classes = getTestClass(arg);
            for (Class<?> cls : classes) {
                Request request = Request.aClass(cls);
                requests.add(request);
            }
        }
        return requests;
    }
}
