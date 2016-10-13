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

public class MethodCallSymbolSerializerTest extends GradleTestBase {

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

        final Position begin1 = new Position(10, 1);
        final Position end1 = new Position(11, 20);
        final Range range1 = new Range(begin1, end1);

        final Position begin2 = new Position(10, 5);
        final Position end2 = new Position(11, 10);
        final Range range2 = new Range(begin2, end2);

        String returnType = "java.lang.String";

        final MethodCallSymbol symbol = new MethodCallSymbol("scope", "fieldName", range1, range2, "declaringClass");
        symbol.returnType = returnType;

        kryoPool.run(new KryoCallback<MethodCallSymbol>() {
            @Override
            public MethodCallSymbol execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, symbol);
                    return symbol;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final MethodCallSymbol methodCallSymbol = kryoPool.run(new KryoCallback<MethodCallSymbol>() {
            @Override
            public MethodCallSymbol execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (MethodCallSymbol) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });
        System.out.println(methodCallSymbol);
        assertEquals(methodCallSymbol.getReturnType(), returnType);

    }
}