package meghanada.reflect.names;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParameterNameVisitor extends VoidVisitorAdapter<Object> {

  private static final Logger log = LogManager.getLogger(ParameterNameVisitor.class);
  final String originClassName;
  String pkg;
  String className;
  MethodParameterNames names = new MethodParameterNames();
  List<MethodParameterNames> parameterNamesList = new ArrayList<>();

  public ParameterNameVisitor(String className) {
    this.originClassName = className;
    this.className = className;
  }

  @Override
  public void visit(PackageDeclaration n, Object arg) {
    this.pkg = n.getName().toString();
    super.visit(n, arg);
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration n, Object arg) {
    super.visit(n, arg);
    final EnumSet<Modifier> modifiers = n.getModifiers();
    if (!modifiers.contains(Modifier.PRIVATE)) {
      final List<BodyDeclaration<?>> members = n.getMembers();
      final SimpleName simpleName = n.getName();
      final String clazz = simpleName.getId();
      // String clazz = n.getName();
      this.className = this.pkg + '.' + clazz;
      log.debug("class {}", this.className);
      int i = 0;
      for (final BodyDeclaration<?> body : members) {
        if (body instanceof MethodDeclaration) {
          MethodDeclaration methodDeclaration = (MethodDeclaration) body;
          this.getParameterNames(methodDeclaration, n.isInterface());
          i++;
        } else if (body instanceof ConstructorDeclaration) {
          // Constructor
        } else if (body instanceof ClassOrInterfaceDeclaration) {
          final ClassOrInterfaceDeclaration classOrInterfaceDeclaration =
              (ClassOrInterfaceDeclaration) body;
          String name = classOrInterfaceDeclaration.getName().getIdentifier();
          String key = this.pkg + '.' + name;
          name = this.originClassName + '.' + name;
          for (MethodParameterNames mpn : this.parameterNamesList) {
            if (mpn != null && mpn.className != null && mpn.className.equals(key)) {
              mpn.className = name;
            }
          }
        }
      }

      if (i > 0 && this.names.className != null) {
        this.parameterNamesList.add(this.names);
        this.names = new MethodParameterNames();
      }
    }
  }

  private void getParameterNames(MethodDeclaration methodDeclaration, boolean isInterface) {
    final EnumSet<Modifier> modifiers = methodDeclaration.getModifiers();
    if (isInterface || modifiers.contains(Modifier.PUBLIC)) {
      String methodName = methodDeclaration.getName().getIdentifier();
      List<Parameter> parameters = methodDeclaration.getParameters();
      names.className = this.className;
      List<List<ParameterName>> parameterNames =
          names.names.computeIfAbsent(methodName, k -> new ArrayList<>(4));

      final List<ParameterName> temp = new ArrayList<>();
      for (final Parameter parameter : parameters) {
        ParameterName parameterName = new ParameterName();
        String type = parameter.getType().toString();
        String name = parameter.getName().getIdentifier();
        if (name.contains("[]")) {
          type = type + "[]";
          name = name.replace("[]", "");
        }
        parameterName.type = type;
        parameterName.name = name;
        temp.add(parameterName);
      }
      parameterNames.add(temp);
    }
  }
}
