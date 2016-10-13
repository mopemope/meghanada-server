package meghanada.parser.source;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import meghanada.GradleTestBase;
import meghanada.reflect.asm.CachedASMReflector;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExpressionScopeSerializerTest extends GradleTestBase {

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void testSerialize1() throws IOException {
        final KryoPool kryoPool = CachedASMReflector.getInstance().getKryoPool();
        final File tempFile = File.createTempFile("0000", "11111");

        final Position begin = new Position(10, 1);
        final Position end = new Position(11, 2);
        final Range range = new Range(begin, end);
        final ExpressionScope expressionScope = new ExpressionScope("scope", range);
        System.out.println(expressionScope);

        kryoPool.run(new KryoCallback<ExpressionScope>() {
            @Override
            public ExpressionScope execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, expressionScope);
                    return expressionScope;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final ExpressionScope expressionScope1 = kryoPool.run(new KryoCallback<ExpressionScope>() {
            @Override
            public ExpressionScope execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (ExpressionScope) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        System.out.println(expressionScope1);

        assertEquals(expressionScope1.getName(), expressionScope.getName());
        assertEquals(expressionScope1.getRange(), expressionScope.getRange());

    }

    @Test
    public void testSerialize2() throws IOException {
        final KryoPool kryoPool = CachedASMReflector.getInstance().getKryoPool();
        final File tempFile = File.createTempFile("0000", "11111");

        final Position begin = new Position(10, 1);
        final Position end = new Position(10, 40);
        final Range range = new Range(begin, end);

        final ExpressionScope expressionScope = new ExpressionScope("scope", range);

        final Position b1 = new Position(10, 1);
        final Position e1 = new Position(10, 2);
        final Range r1 = new Range(b1, e1);
        String returnType = "java.lang.String";
        final FieldAccessSymbol fas = new FieldAccessSymbol("scope", "fieldName", r1, "declaringClass");
        fas.returnType = returnType;
        expressionScope.addFieldAccess(fas);

        final Position b2 = new Position(10, 10);
        final Position e2 = new Position(10, 39);
        final Range r2 = new Range(b2, e2);
        final Range r3 = new Range(new Position(10, 1), new Position(10, 5));

        final MethodCallSymbol mcs = new MethodCallSymbol("scope", "methodName", r2, r3, "declaringClass");
        mcs.returnType = "java.util.List";

        expressionScope.addMethodCall(mcs);

        System.out.println(expressionScope);

        kryoPool.run(new KryoCallback<ExpressionScope>() {
            @Override
            public ExpressionScope execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, expressionScope);
                    return expressionScope;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final ExpressionScope expressionScope1 = kryoPool.run(new KryoCallback<ExpressionScope>() {
            @Override
            public ExpressionScope execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (ExpressionScope) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });
        System.out.println(expressionScope1);
        assertEquals(expressionScope1.getName(), expressionScope.getName());
        assertEquals(expressionScope1.getRange(), expressionScope.getRange());
    }

}
