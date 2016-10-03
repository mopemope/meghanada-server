package meghanada.reflect.names;

import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ParameterNameVisitor extends VoidVisitorAdapter<Object> {

    private final static Logger log = LogManager.getLogger(ParameterNameVisitor.class);

    String pkg;
    String originClassName;
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
        int mod = n.getModifiers();

        if (!ModifierSet.isPrivate(mod)) {
            List<BodyDeclaration> members = n.getMembers();
            String clazz = n.getName();
            this.className = this.pkg + "." + clazz;
            log.debug("class {}", this.className);
            int i = 0;
            for (BodyDeclaration body : members) {
                if (body instanceof MethodDeclaration) {
                    MethodDeclaration methodDeclaration = (MethodDeclaration) body;
                    this.getParameterNames(methodDeclaration, n.isInterface());
                    i++;
                } else if (body instanceof ConstructorDeclaration) {
                    // Constructor
                } else if (body instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration classOrInterfaceDeclaration = (ClassOrInterfaceDeclaration) body;
                    String name = classOrInterfaceDeclaration.getName();
                    String key = this.pkg + "." + name;
                    name = this.originClassName + "." + name;
                    for (MethodParameterNames mpn : this.parameterNamesList) {
                        if (mpn != null
                                && mpn.className != null
                                && mpn.className.equals(key)) {
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
        int mod = methodDeclaration.getModifiers();
        if (isInterface || ModifierSet.isPublic(mod)) {
            String methodName = methodDeclaration.getName();
            List<Parameter> parameters = methodDeclaration.getParameters();
            names.className = this.className;
            List<List<ParameterName>> parameterNames = names.names.get(methodName);
            if (parameterNames == null) {
                parameterNames = new ArrayList<>();
                names.names.put(methodName, parameterNames);
            }

            List<ParameterName> temp = new ArrayList<>();
            for (Parameter parameter : parameters) {
                ParameterName parameterName = new ParameterName();
                String type = parameter.getType().toString();
                String name = parameter.getId().toString();
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
