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

import static org.junit.Assert.fail;

public class JavaSourceSerializerTest extends GradleTestBase {

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    private TypeScope getTypeScope() {
        final Position begin = new Position(10, 1);
        final Position end = new Position(13, 30);
        final Range range = new Range(begin, end);
        final Range nameRange = new Range(new Position(10, 1), new Position(10, 8));
        return new TypeScope("com.example", "Foo", range, nameRange);
    }

    @Test
    public void testSerialize1() throws IOException {
        final File tempFile = File.createTempFile("11111", "2222");
        final File file = File.createTempFile("aaaaa", "bbbbb");

        final KryoPool kryoPool = CachedASMReflector.getInstance().getKryoPool();

        final JavaSource source = new JavaSource(file);
        source.importClass.put("java.lang.String", "String");
        source.typeScopes.add(getTypeScope());
        source.pkg = "com.example";
        source.unknownClass.add("Unknown");

        kryoPool.run(new KryoCallback<JavaSource>() {
            @Override
            public JavaSource execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, source);
                    return source;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final JavaSource source1 = kryoPool.run(new KryoCallback<JavaSource>() {
            @Override
            public JavaSource execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (JavaSource) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });
        System.out.println(source);
        System.out.println(source1);
    }

}