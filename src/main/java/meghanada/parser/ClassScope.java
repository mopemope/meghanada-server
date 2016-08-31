package meghanada.parser;

import com.github.javaparser.Range;
import com.google.common.base.MoreObjects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ClassScope extends TypeScope {

    private boolean isInterface;
    private List<String> extendsClasses;
    private List<String> implClasses;
    private List<String> typeParameters;
    private Map<String, String> typeParameterMap = new HashMap<>();

    ClassScope(final String pkg, final String type, final Range range, final Range nameRange, final boolean isInterface) {
        super(pkg, type, range, nameRange);
        this.isInterface = isInterface;
    }

    boolean isInterface() {
        return isInterface;
    }

    List<String> getExtendsClasses() {
        return extendsClasses;
    }

    void setExtendsClasses(List<String> extendsClasses) {
        this.extendsClasses = extendsClasses;
    }

    List<String> getImplClasses() {
        return implClasses;
    }

    void setImplClasses(List<String> implClasses) {
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
