package meghanada.reflect.asm;

import com.google.common.base.MoreObjects;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.MethodDescriptor;
import meghanada.reflect.MethodParameter;
import meghanada.reflect.names.MethodParameterNames;
import meghanada.reflect.names.ParameterName;
import meghanada.store.Serializer;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.EntryMessage;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;

class MethodAnalyzeVisitor extends MethodVisitor {

  private static final String CONSTRUCTOR = "<init>";
  private static final String SEP = ", ";
  private static final Logger log = LogManager.getLogger(MethodAnalyzeVisitor.class);
  private final ClassAnalyzeVisitor classAnalyzeVisitor;
  private final int access;
  private final String name;
  private final String[] exceptions;
  private final String methodSignature;
  private final boolean interfaceMethod;
  private final int[] lvtSlotIndex;
  private final boolean hasVarargs;

  private boolean hasDefault;
  private String[] parameterNames;
  private List<TypeInfo> parameterTypes;
  private Set<String> typeParameters;
  private TypeInfo formalType;
  private TypeInfo returnType;
  private Map<String, String> typeMap;

  MethodAnalyzeVisitor(
      final ClassAnalyzeVisitor classAnalyzeVisitor,
      final int access,
      final String name,
      final String desc,
      final String signature,
      final String[] exceptions) {
    super(Opcodes.ASM5);
    final EntryMessage entryMessage =
        log.traceEntry(
            "classAnalyzeVisitor={} access={} name={} desc={} signature={} exceptions={}",
            classAnalyzeVisitor,
            access,
            name,
            desc,
            signature,
            exceptions);
    this.classAnalyzeVisitor = classAnalyzeVisitor;
    this.access = access;
    this.name = name;
    this.exceptions = exceptions;

    Type[] args = Type.getArgumentTypes(desc);
    this.parameterNames = new String[args.length];
    boolean isStatic = (Opcodes.ACC_STATIC & access) > 0;
    this.lvtSlotIndex = computeLvtSlotIndices(isStatic, args);
    this.hasVarargs = (Opcodes.ACC_VARARGS & access) > 0;
    String target = desc;
    if (signature != null) {
      target = signature;
    }
    this.methodSignature = target;
    this.interfaceMethod = this.classAnalyzeVisitor.getClassIndex().isInterface();

    // log.trace("name:{} sig:{}", name, target);
    // log.trace("classIndex:{}", classAnalyzeVisitor.getClassIndex().isInterface);
    // log.debug("methodName {} desc {} sig {}", name, desc, signature);
    log.traceExit(entryMessage);
  }

  private static int[] computeLvtSlotIndices(boolean isStatic, Type[] paramTypes) {
    int[] lvtIndex = new int[paramTypes.length];
    int nextIndex = isStatic ? 0 : 1;
    for (int i = 0; i < paramTypes.length; i++) {
      lvtIndex[i] = nextIndex;
      if (isWideType(paramTypes[i])) {
        nextIndex += 2;
      } else {
        nextIndex++;
      }
    }
    return lvtIndex;
  }

  private static boolean isWideType(Type aType) {
    return aType.equals(Type.LONG_TYPE) || aType.equals(Type.DOUBLE_TYPE);
  }

  MethodAnalyzeVisitor setTypeMap(Map<String, String> typeMap) {
    this.typeMap = typeMap;
    return this;
  }

  MethodAnalyzeVisitor parseSignature() {
    final EntryMessage entryMessage =
        log.traceEntry("name={} methodSignature={}", this.name, this.methodSignature);
    final boolean isStatic = (Opcodes.ACC_STATIC & this.access) > 0;
    final SignatureReader signatureReader = new SignatureReader(this.methodSignature);
    MethodSignatureVisitor visitor;
    if (isStatic) {
      visitor = new MethodSignatureVisitor(this.name, new ArrayList<>(4));
    } else {
      visitor = new MethodSignatureVisitor(this.name, this.classAnalyzeVisitor.classTypeParameters);
    }
    if (this.typeMap != null) {
      visitor.setTypeMap(this.typeMap);
    }

    signatureReader.accept(visitor);

    this.formalType = visitor.getFormalType();
    this.parameterTypes = visitor.getParameterTypes();
    this.typeParameters = visitor.getTypeParameters();
    this.returnType = visitor.getReturnType();
    log.traceExit(entryMessage);
    return this;
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(int i, String s, boolean b) {
    log.traceEntry("i={}, s={} b={}", i, s, b);
    return log.traceExit(super.visitParameterAnnotation(i, s, b));
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    log.traceEntry("attribute={}", attribute);
    super.visitAttribute(attribute);
    log.traceExit();
  }

  @Override
  public void visitParameter(String s, int i) {
    log.traceEntry("s={} i={}", s, i);
    super.visitParameter(s, i);
    log.traceExit();
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(
      int i, TypePath typePath, Label[] labels, Label[] labels1, int[] ints, String s, boolean b) {
    log.traceEntry("i={} s={}", i, s);
    final AnnotationVisitor annotationVisitor =
        super.visitLocalVariableAnnotation(i, typePath, labels, labels1, ints, s, b);
    return log.traceExit(annotationVisitor);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    log.traceEntry("desc={} visible={}", desc);
    return log.traceExit(super.visitAnnotation(desc, visible));
  }

  @Override
  public void visitLocalVariable(
      String name, String description, String signature, Label start, Label end, int index) {
    log.traceEntry(
        "name={} description={} signature={} start={} endPos={} index={}",
        name,
        description,
        signature,
        start,
        end,
        index);
    // boolean hasLvtInfo = true;
    for (int i = 0; i < this.lvtSlotIndex.length; i++) {
      if (this.lvtSlotIndex[i] == index) {
        this.parameterNames[i] = name;
      }
    }
    log.traceExit();
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
      int typeRef, TypePath typePath, String desc, boolean visible) {
    log.traceEntry("typeRef={} typePath={} desc={} visible={}", typeRef, typePath, desc, desc);
    final AnnotationVisitor annotationVisitor =
        super.visitTypeAnnotation(typeRef, typePath, desc, visible);
    return log.traceExit(annotationVisitor);
  }

  @Override
  public void visitCode() {
    final EntryMessage entryMessage = log.traceEntry("name={}", this.name);
    if (this.interfaceMethod) {
      this.hasDefault = true;
    }
    super.visitCode();
    log.traceExit(entryMessage);
  }

  @Override
  public void visitEnd() {
    final EntryMessage entryMessage =
        log.traceEntry("returnType={} parameterTypes={}", this.returnType, this.parameterTypes);
    if (this.returnType == null) {
      // void
      this.returnType = new TypeInfo("void", "void");
    }

    if (this.parameterTypes == null) {
      // void
      this.parameterTypes = new ArrayList<>(4);
    }

    if (((this.parameterTypes.size() != this.parameterNames.length)
            || (this.parameterNames.length > 0 && this.parameterNames[0] == null))
        && !tryGetParameterName(this.classAnalyzeVisitor.className, this.name)) {
      this.setDefaultParameterNames();
    }

    int size = this.parameterTypes.size();
    for (int i = 0; i < size; i++) {
      TypeInfo ti = this.parameterTypes.get(i);
      ti.paramName = this.parameterNames[i];
      if (i == size - 1 && this.hasVarargs) {
        ti.variableArguments = true;
      }
    }
    // log.debug("{} ({})", this.name, this.parameterTypes);
    this.toMemberDescriptor();
    log.traceExit(entryMessage);
  }

  private boolean tryGetParameterName(final String className, final String name) {
    // log.debug("search {}", name);
    final String path = StringUtils.replace(className, ".", "/");
    try (final InputStream in = getClass().getResourceAsStream("/params/" + path + ".param")) {
      if (in == null) {
        return false;
      }

      final MethodParameterNames mn = Serializer.readObject(in, MethodParameterNames.class);
      final List<List<ParameterName>> pmsList = mn.names.get(name);
      if (pmsList == null) {
        return false;
      }
      return this.searchParameterNames(pmsList);
      // fallback
    } catch (Exception e) {
      log.debug(e.getMessage());
    }

    return false;
  }

  private boolean searchParameterNames(List<List<ParameterName>> pmsList) {
    for (List<ParameterName> pms : pmsList) {
      if (this.parameterTypes.size() != pms.size()) {
        continue;
      }
      boolean match = true;
      int last = this.parameterTypes.size() - 1;
      for (int i = 0; i < this.parameterTypes.size(); i++) {
        TypeInfo ti = this.parameterTypes.get(i);
        ParameterName parameterName = pms.get(i);
        String typeInfoType = ti.toString();
        String parameterNameType = parameterName.type;
        // remove generics
        int idx1 = typeInfoType.indexOf('<');
        if (idx1 > 0) {
          typeInfoType = typeInfoType.substring(0, idx1);
        }
        if (i == last) {
          typeInfoType = StringUtils.replace(typeInfoType, "...", "");
        }
        // replace mark
        typeInfoType = StringUtils.replace(typeInfoType, "%%", "");
        int idx2 = parameterNameType.indexOf('<');
        if (idx2 > 0) {
          parameterNameType = parameterNameType.substring(0, idx2);
        }
        // log.trace("search parameterName idx:{} {}/{}", i, typeInfoType, parameterNameType);
        if (typeInfoType.equals(parameterNameType)) {
          // log.trace("@ match parameterName idx:{} {}/{}", i, typeInfoType, parameterNameType);
          this.parameterNames[i] = parameterName.name;
        } else {
          // fail
          match = false;
        }
      }
      if (match) {
        // all OK
        return true;
      }
    }
    return false;
  }

  private void setDefaultParameterNames() {
    List<String> temp = new ArrayList<>(4);
    for (int i = 0; i < this.parameterTypes.size(); i++) {
      String tempName = String.format("arg%d", i);
      temp.add(tempName);
    }
    this.parameterNames = temp.toArray(new String[this.parameterTypes.size()]);
  }

  private void toMemberDescriptor() {
    String modifier = ASMReflector.toModifier(this.access, this.hasDefault);
    if (this.interfaceMethod) {
      modifier = StringUtils.replace(modifier, "abstract", "").trim();
    }

    final CandidateUnit.MemberType memberType =
        name.equals("<init>")
            ? CandidateUnit.MemberType.CONSTRUCTOR
            : CandidateUnit.MemberType.METHOD;
    final String methodName =
        memberType == CandidateUnit.MemberType.CONSTRUCTOR
            ? this.classAnalyzeVisitor.className
            : this.name;
    final EntryMessage message =
        log.traceEntry(
            "className={} memberType={} methodName={} returnType={}",
            this.classAnalyzeVisitor.className,
            methodName,
            memberType,
            this.returnType);

    if (methodName.startsWith("lambda$")) {
      // skip
      log.traceExit(message);
      return;
    }
    if (memberType == CandidateUnit.MemberType.CONSTRUCTOR
        && methodName.equals(ClassNameUtils.OBJECT_CLASS)) {
      // skip
      log.traceExit(message);
      return;
    }

    final String returnFQCN =
        memberType == CandidateUnit.MemberType.CONSTRUCTOR ? methodName : this.returnType.getFQCN();

    final List<MethodParameter> methodParameters =
        this.parameterTypes
            .stream()
            .map(
                typeInfo ->
                    new MethodParameter(
                        typeInfo.getFQCN(), typeInfo.paramName, typeInfo.variableArguments))
            .collect(Collectors.toList());

    final MethodDescriptor md =
        new MethodDescriptor(
            this.classAnalyzeVisitor.className,
            methodName,
            modifier,
            methodParameters,
            this.exceptions,
            returnFQCN,
            this.hasDefault,
            memberType);
    md.hasVarargs = this.hasVarargs;
    md.setTypeParameters(this.typeParameters);
    log.trace("formalType={}", this.formalType);
    if (this.formalType != null) {
      md.formalType = this.formalType.toString();
    }
    this.classAnalyzeVisitor.members.add(md);
    log.traceExit(message);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("access", access)
        .add("name", name)
        .add("exceptions", exceptions)
        .add("methodSignature", methodSignature)
        .add("interfaceMethod", interfaceMethod)
        .add("lvtSlotIndex", lvtSlotIndex)
        .add("hasDefault", hasDefault)
        .add("parameterNames", parameterNames)
        .add("parameterTypes", parameterTypes)
        .add("typeParameters", typeParameters)
        .add("formalType", formalType)
        .add("returnType", returnType)
        .add("typeMap", typeMap)
        .toString();
  }
}
