package meghanada;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import meghanada.parser.source.ExpressionScope;

public class Jump1 {

    public void test() {
        final Range range = new Range(new Position(10, 1), new Position(11, 2));
        final ExpressionScope scope = new ExpressionScope("name", range);
        final String name = scope.getName();
    }
}
