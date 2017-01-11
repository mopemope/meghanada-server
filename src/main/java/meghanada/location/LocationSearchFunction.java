package meghanada.location;

import meghanada.analyze.Source;

@FunctionalInterface
interface LocationSearchFunction {

    Location apply(Source javaSource, Integer line, Integer column, String symbol);

}
