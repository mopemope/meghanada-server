package meghanada.parser;

import java.util.Optional;

@FunctionalInterface
interface AnalyzeReturnTypeFunction {

    Optional<String> apply(String name, String declaringClass, boolean isLocal, boolean isField, JavaSource source);

}
