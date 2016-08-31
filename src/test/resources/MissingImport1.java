import java.util.Date;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;

import java.util.jar.JarEntry;

public class MissingImport1 extends File {

    Date date;
    java.util.Queue queue;

    MissingImport1(Collection c)  {

    }

    public List<String> testSimple() {
        Map<String, Long> foo = new HashMap<>();
        return new ArrayList<String>();
    }

    public Properties getProp(Set<ABC> set) throws IOException {
        return null;
    }
}

