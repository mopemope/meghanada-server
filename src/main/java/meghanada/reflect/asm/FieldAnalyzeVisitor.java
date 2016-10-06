package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import meghanada.reflect.FieldDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;

import java.util.ArrayList;
import java.util.Map;

class FieldAnalyzeVisitor extends FieldVisitor {

    private static Logger log = LogManager.getLogger(FieldAnalyzeVisitor.class);

    private final ClassAnalyzeVisitor classAnalyzeVisitor;
    private final int access;
    private final String name;
    private final String fieldSignature;
    private FieldSignatureVisitor fieldSignatureVisitor;
    private Map<String, String> typeMap;

    FieldAnalyzeVisitor(ClassAnalyzeVisitor classAnalyzeVisitor, int access, String name, String desc, String sig) {
        super(Opcodes.ASM5);
        this.classAnalyzeVisitor = classAnalyzeVisitor;
        this.access = access;
        this.name = name;

        // read desc
        String signature = desc;
        if (sig != null) {
            signature = sig;
        }
        this.fieldSignature = signature;
        log.trace("fieldSignature={}", fieldSignature);
    }

    FieldAnalyzeVisitor parseSignature() {
        final EntryMessage m = log.traceEntry("fieldSignature={}", fieldSignature);
        boolean isStatic = (Opcodes.ACC_STATIC & this.access) > 0;
        SignatureReader signatureReader = new SignatureReader(this.fieldSignature);
        FieldSignatureVisitor visitor;
        if (isStatic) {
            visitor = new FieldSignatureVisitor(this.name, new ArrayList<>(4));
        } else {
            visitor = new FieldSignatureVisitor(this.name, this.classAnalyzeVisitor.classTypeParameters);
        }
        if (this.typeMap != null) {
            visitor.setTypeMap(this.typeMap);
        }

        this.fieldSignatureVisitor = visitor;
        signatureReader.acceptType(fieldSignatureVisitor);
        return log.traceExit(m, this);
    }

    @Override
    public void visitEnd() {
        final EntryMessage m = log.traceEntry("fieldSignature={}", fieldSignature);
        final String modifier = ASMReflector.toModifier(access, false);
        final String fqcn = fieldSignatureVisitor.getResult();
        final FieldDescriptor fd = new FieldDescriptor(this.classAnalyzeVisitor.className, this.name, modifier, fqcn);
        fd.typeParameters = fieldSignatureVisitor.getTypeParameters();
        this.classAnalyzeVisitor.getMembers().add(fd);
        log.traceExit(m);
    }

    FieldAnalyzeVisitor setTypeMap(Map<String, String> typeMap) {
        this.typeMap = typeMap;
        return this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("access", access)
                .add("name", name)
                .add("fieldSignature", fieldSignature)
                .add("typeMap", typeMap)
                .toString();
    }
}
