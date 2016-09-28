package meghanada.parser.source;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DefaultSerializer(ClassScopeSerializer.class)
public class ClassScope extends TypeScope {

    public boolean isInterface;
    public List<String> extendsClasses;
    public List<String> implClasses;
    public List<String> typeParameters;
    public Map<String, String> typeParameterMap = new HashMap<>();

    public ClassScope(final String pkg, final String type, final Range range, final Range nameRange, final boolean isInterface) {
        super(pkg, type, range, nameRange);
        this.isInterface = isInterface;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public List<String> getExtendsClasses() {
        return extendsClasses;
    }

    public void setExtendsClasses(List<String> extendsClasses) {
        this.extendsClasses = extendsClasses;
    }

    public List<String> getImplClasses() {
        return implClasses;
    }

    public void setImplClasses(List<String> implClasses) {
        this.implClasses = implClasses;
    }

    public List<String> getTypeParameters() {
        return typeParameters;
    }

    public void setTypeParameters(List<String> typeParameters) {
        this.typeParameters = typeParameters;
    }

    public Map<String, String> getTypeParameterMap() {
        return typeParameterMap;
    }

    public void setTypeParameterMap(Map<String, String> typeParameterMap) {
        this.typeParameterMap = typeParameterMap;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("isInterface", isInterface)
                .add("extendsClasses", extendsClasses)
                .add("implClasses", implClasses)
                .add("typeParameters", typeParameters)
                .add("typeParameterMap", typeParameterMap)
                .toString();
    }
}
