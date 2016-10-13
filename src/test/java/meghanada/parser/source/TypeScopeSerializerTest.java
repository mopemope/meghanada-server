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

public class TypeScopeSerializerTest extends GradleTestBase {

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
        final Position end = new Position(13, 30);
        final Range range = new Range(begin, end);
        final Range nameRange = new Range(new Position(10, 1), new Position(10, 8));

        final TypeScope typeScope = new TypeScope("com.example", "Foo", range, nameRange);
        System.out.println(typeScope);

        kryoPool.run(new KryoCallback<TypeScope>() {
            @Override
            public TypeScope execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, typeScope);
                    return typeScope;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final TypeScope typeScope1 = kryoPool.run(new KryoCallback<TypeScope>() {
            @Override
            public TypeScope execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (TypeScope) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        System.out.println(typeScope1);

        assertEquals(typeScope1.getName(), typeScope.getName());
        assertEquals(typeScope1.getRange(), typeScope.getRange());

    }


}
