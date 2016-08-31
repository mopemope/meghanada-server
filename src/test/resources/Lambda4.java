import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Lambda4 {

    private void println(Object o) {
        System.out.println(o);
    }

    public void test1() {
        File file = new File();
        Files.walk(file.toPath())
                .parallel()
                .map(Path::toFile)
                .filter(f ->  (f.isFile() && f.getName().endsWith(".class")))
                .forEach(classFile -> {
                    this.println(classFile);
                });
    }

}
