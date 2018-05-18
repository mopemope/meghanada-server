package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.utils.ClassNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;

class ClassAnalyzeVisitor extends ClassVisitor {

  private static final String CLASS_INIT = "<init>";
  private static final String CLINIT = "<clinit>";
  private static final String FUNCTIONAL = "Ljava/lang/FunctionalInterface;";
  private static Logger log = LogManager.getLogger(ClassAnalyzeVisitor.class);

  final String className;
  final List<MemberDescriptor> members;
  private final boolean classOnly;
  private final boolean includePrivate;
  List<String> classTypeParameters;
  private ClassIndex classIndex;
  private String classNameWithType;

  ClassAnalyzeVisitor(
      final String className, final boolean classOnly, final boolean includePrivate) {
    super(Opcodes.ASM5);
    this.className = className;
    this.classOnly = classOnly;
    this.includePrivate = includePrivate;
    this.members = new ArrayList<>(64);
  }

  ClassAnalyzeVisitor(
      final String className,
      final String classNameWithTP,
      final boolean classOnly,
      final boolean includePrivate) {
    this(className, classOnly, includePrivate);
    this.classNameWithType = classNameWithTP;
  }

  private ClassAnalyzeVisitor(String className) {
    this(className, false, false);
  }

  @Override
  public void visit(
      int api, int access, String name, String signature, String superClass, String[] interfaces) {
    // log.debug("Name:{}", name);
    // call class
    final boolean isInterface = (Opcodes.ACC_INTERFACE & access) == Opcodes.ACC_INTERFACE;
    // log.debug("name {} sig {} IF:{}", name, signature, isInterface);

    if (signature != null) {
      // generics
      // log.debug("name {} sig {}", name, signature);
      final SignatureReader signatureReader = new SignatureReader(signature);
      ClassSignatureVisitor classSignatureVisitor =
          new ClassSignatureVisitor(this.className, isInterface);
      signatureReader.accept(classSignatureVisitor);

      this.classTypeParameters = classSignatureVisitor.getTypeParameters();
      this.classIndex = classSignatureVisitor.getClassIndex();
      if (!this.classIndex.getSupers().contains(ClassNameUtils.OBJECT_CLASS)) {
        this.classIndex.addSuper(ClassNameUtils.OBJECT_CLASS);
      }
    } else {
      this.classTypeParameters = new ArrayList<>(4);
      final List<String> supers = new ArrayList<>(4);
      if (superClass != null) {
        final String superClassFQCN = ClassNameUtils.replaceSlash(superClass);
        supers.add(superClassFQCN);
      }
      Arrays.stream(interfaces)
          .forEach(interfaceName -> supers.add(ClassNameUtils.replaceSlash(interfaceName)));
      this.classIndex = new ClassIndex(this.className, new ArrayList<>(4), supers);
      this.classIndex.setInterface(isInterface);
    }
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String sig, String[] exceptions) {
    if (this.classOnly) {
      return super.visitMethod(access, name, desc, sig, exceptions);
    }

    // log.debug("Method Name:{}", name);
    if (name.contains("$")) {
      return super.visitMethod(access, name, desc, sig, exceptions);
    }
    if (includePrivate && !name.equals(CLINIT)) {
      // method
      if (this.classNameWithType == null) {
        return new MethodAnalyzeVisitor(this, access, name, desc, sig, exceptions).parseSignature();
      } else {
        return new MethodAnalyzeVisitor(this, access, name, desc, sig, exceptions)
            .setTypeMap(this.getTypeMap())
            .parseSignature();
      }
    }
    if ((Opcodes.ACC_PRIVATE & access) == 0 && !name.equals(CLINIT)) {
      // method
      if (this.classNameWithType == null) {
        return new MethodAnalyzeVisitor(this, access, name, desc, sig, exceptions).parseSignature();
      } else {
        return new MethodAnalyzeVisitor(this, access, name, desc, sig, exceptions)
            .setTypeMap(this.getTypeMap())
            .parseSignature();
      }
    }
    return super.visitMethod(access, name, desc, sig, exceptions);
  }

  //    @Override
  //    public void visitInnerClass(String name, String outerName, String innerName, int access) {
  //        log.debug("visitInnerClass {} {} {} {}", this.className, name, outerName, innerName);
  //        super.visitInnerClass(name, outerName, innerName, access);
  //    }

  //    @Override
  //    public void visitOuterClass(String s, String s1, String s2) {
  //        log.debug("visitOuterClass {} {} {} {}", this.className, s, s1, s2);
  //        super.visitOuterClass(s, s1, s2);
  //    }

  @Override
  public FieldVisitor visitField(int access, String name, String desc, String sig, Object o) {
    if (this.classOnly) {
      return super.visitField(access, name, desc, sig, o);
    }
    if (name.startsWith("$") || name.startsWith("this$")) {
      return super.visitField(access, name, desc, sig, o);
    }
    if (includePrivate) {
      return new FieldAnalyzeVisitor(this, access, name, desc, sig)
          .setTypeMap(this.getTypeMap())
          .parseSignature();
    }
    // log.debug("Field Name:{}", name);
    if ((Opcodes.ACC_PRIVATE & access) == 0) {
      return new FieldAnalyzeVisitor(this, access, name, desc, sig)
          .setTypeMap(this.getTypeMap())
          .parseSignature();
    }
    return super.visitField(access, name, desc, sig, o);
  }

  List<MemberDescriptor> getMembers() {
    return this.members;
  }

  ClassIndex getClassIndex() {
    return classIndex;
  }

  private Map<String, String> getTypeMap() {
    Map<String, String> result = new HashMap<>(4);
    List<String> types = this.classTypeParameters;
    List<String> realTypes = ClassNameUtils.parseTypeParameter(this.classNameWithType);

    for (int i = 0; i < types.size(); i++) {
      final String key = types.get(i);
      if (realTypes.size() > i) {
        final String real = realTypes.get(i);
        result.putIfAbsent(key, real);
      }
    }
    return result;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    if (visible && desc.equals(FUNCTIONAL)) {
      this.classIndex.setFunctional(true);
    }
    return super.visitAnnotation(desc, visible);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("className", className)
        .add("classTypeParameters", classTypeParameters)
        .add("classIndex", classIndex)
        .add("classNameWithType", classNameWithType)
        .toString();
  }
}
