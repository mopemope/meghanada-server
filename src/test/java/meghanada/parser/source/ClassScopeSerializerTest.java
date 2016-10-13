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

public class ClassScopeSerializerTest extends GradleTestBase {

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

        final ClassScope classScope = new ClassScope("com.example", "Foo", range, nameRange, false);
        classScope.typeParameterMap.put("V", "java.lang.String");
        System.out.println(classScope);

        kryoPool.run(new KryoCallback<ClassScope>() {
            @Override
            public ClassScope execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, classScope);
                    return classScope;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final ClassScope classScope1 = kryoPool.run(new KryoCallback<ClassScope>() {
            @Override
            public ClassScope execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (ClassScope) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        System.out.println(classScope1);

        assertEquals(classScope1.getName(), classScope.getName());
        assertEquals(classScope1.getRange(), classScope.getRange());

    }

}
