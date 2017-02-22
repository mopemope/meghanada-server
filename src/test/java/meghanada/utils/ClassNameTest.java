package meghanada.utils;

import meghanada.GradleTestBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClassNameTest extends GradleTestBase {


    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
    }

    @Test
    public void getName1() throws Exception {
        ClassName className = new ClassName("Map.Entry<String, Long>");
        String name = className.getName();
        assertEquals("Map.Entry", name);
    }

    @Test
    public void getName2() throws Exception {
        ClassName className = new ClassName("Map.Entry<String, Long>[]");
        String name = className.getName();
        assertEquals("Map.Entry", name);
    }

    @Test
    public void getName3() throws Exception {
        ClassName className = new ClassName("Map<K, V>.Entry");
        String name = className.getName();
        assertEquals("Map.Entry", name);
    }

    @Test
    public void getName4() throws Exception {
        ClassName className = new ClassName("ThreadLocal<int[]>");
        String name = className.getName();
        assertEquals("ThreadLocal", name);
    }

    @Test
    public void getName5() throws Exception {
        ClassName className = new ClassName("Map<Object[], Void>");
        String name = className.getName();
        assertEquals("Map", name);
    }

}