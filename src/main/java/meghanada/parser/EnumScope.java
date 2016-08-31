package meghanada.parser;

import com.github.javaparser.Range;

import java.util.List;

public class EnumScope extends TypeScope {

    private List<String> implClasses;
    private int index = 0;

    EnumScope(final String pkg, final String type, final Range range, final Range nameRange) {
        super(pkg, type, range, nameRange);
    }

    public List<String> getImplClasses() {
        return implClasses;
    }

    public void setImplClasses(List<String> implClasses) {
        this.implClasses = implClasses;
    }

    public int getIndex() {
        return index;
    }

    public void incrIndex() {
        this.index++;
    }
}
