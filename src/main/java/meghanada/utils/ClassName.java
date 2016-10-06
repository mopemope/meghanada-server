package meghanada.utils;

import com.google.common.base.MoreObjects;
import com.google.common.collect.BiMap;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ClassName {

    private static final Logger log = LogManager.getLogger(ClassName.class);

    private final String rawName;
    private final int typeIndex;
    private final int typeLastIndex;
    private final int arrayIndex;

    public ClassName(String name) {
        this.rawName = ClassNameUtils.vaArgsToArray(name);
        this.typeIndex = this.rawName.indexOf("<");
        this.typeLastIndex = this.rawName.lastIndexOf(">");
        this.arrayIndex = this.rawName.indexOf("[");
    }

    public boolean hasTypeParameter() {
        return this.typeIndex > 0;
    }

    public String toFQCN(String ownPkg, BiMap<String, String> classes) {

        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        final String name = this.getName();

        if (cachedASMReflector.containsFQCN(name)) {
            return rawName;
        }

        if (classes != null && classes.containsKey(name)) {
            return this.addTypeParameters(classes.get(name));
        }

        {
            if (ownPkg != null) {
                final String result = cachedASMReflector.classNameToFQCN(ownPkg + '.' + name);
                if (result != null) {
                    return this.addTypeParameters(result);
                }
            }
        }

        // full search
        final String result = cachedASMReflector.classNameToFQCN(name);
        if (result != null) {
            return this.addTypeParameters(result);
        }
        return null;
    }

    public String getName() {
        String name = ClassNameUtils.removeCapture(this.rawName);
        if (typeIndex >= 0) {
            String fst = name.substring(0, typeIndex);
            String sec = name.substring(typeLastIndex + 1, name.length());
            name = fst + sec;
        }

        int arrayIndex = name.indexOf("[");
        if (arrayIndex >= 0) {
            name = name.substring(0, arrayIndex);
        }
        return name;
    }

    public String addTypeParameters(String name) {
        if (typeIndex >= 0) {
            return name + this.rawName.substring(typeIndex);
        }
        if (arrayIndex >= 0) {
            return name + this.rawName.substring(arrayIndex);
        }
        return name;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rawName", rawName)
                .toString();
    }
}
