package meghanada.location;

import meghanada.parser.source.JavaSource;

@FunctionalInterface
interface LocationSearchFunction {

    Location apply(JavaSource javaSource, Integer line, Integer column, String symbol);

}
