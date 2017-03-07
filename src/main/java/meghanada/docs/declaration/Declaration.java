package meghanada.docs.declaration;

import com.google.common.base.MoreObjects;

public class Declaration {

    public final String scopeInfo;
    public final String signature;
    public final Type type;

    public Declaration(final String scopeInfo, final String signature, final Type type) {
        this.scopeInfo = scopeInfo.trim();
        this.signature = signature.trim();
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("symbol", scopeInfo)
                .add("declaration", signature)
                .add("declaration", signature)
                .toString();
    }

    public enum Type {
        FIELD,
        METHOD,
        CLASS,
        VAR,
        OTHER
    }
}
