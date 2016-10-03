package meghanada;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gen8 {

    private static final Pattern MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN = Pattern.compile(
            "\\s*-XX:MaxDirectMemorySize\\s*=\\s*([0-9]+)\\s*([kKmMgG]?)\\s*$");

    private static long maxDirectMemory0() {

        try {
            Class<?> vmClass = Class.forName("sun.misc.VM", true, ClassLoader.getSystemClassLoader());
            Method m = vmClass.getDeclaredMethod("maxDirectMemory");
        } catch (Throwable ignored) {
            // Ignore
        }


        try {
            Class<?> mgmtFactoryClass = Class.forName(
                    "java.lang.management.ManagementFactory", true, ClassLoader.getSystemClassLoader());
            Class<?> runtimeClass = Class.forName(
                    "java.lang.management.RuntimeMXBean", true, ClassLoader.getSystemClassLoader());

            Object runtime = mgmtFactoryClass.getDeclaredMethod("getRuntimeMXBean").invoke(null);

            @SuppressWarnings("unchecked")
            List<String> vmArgs = (List<String>) runtimeClass.getDeclaredMethod("getInputArguments").invoke(runtime);
            for (int i = vmArgs.size() - 1; i >= 0; i--) {
                Matcher m = MAX_DIRECT_MEMORY_SIZE_ARG_PATTERN.matcher(vmArgs.get(i));
                if (!m.matches()) {
                    continue;
                }

                switch (m.group(2).charAt(0)) {
                }
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        return 0L;
    }

}
