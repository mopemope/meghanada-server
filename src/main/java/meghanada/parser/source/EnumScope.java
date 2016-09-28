package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Range;

import java.util.ArrayList;
import java.util.List;

@DefaultSerializer(EnumScopeSerializer.class)
public class EnumScope extends TypeScope {

    List<String> implClasses = new ArrayList<>();
    int index;

    public EnumScope(final String pkg, final String type, final Range range, final Range nameRange) {
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
