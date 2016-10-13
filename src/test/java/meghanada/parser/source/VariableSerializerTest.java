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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VariableSerializerTest extends GradleTestBase {

    @org.junit.BeforeClass
    public static void beforeClass() throws Exception {
        GradleTestBase.setupReflector();
        CachedASMReflector cachedASMReflector = CachedASMReflector.getInstance();
        cachedASMReflector.addClasspath(getOutputDir());
        cachedASMReflector.createClassIndexes();
    }

    @Test
    public void testSerialize1() throws Exception {
        final KryoPool kryoPool = CachedASMReflector.getInstance().getKryoPool();
        final File tempFile = File.createTempFile("0000", "11111");

        final Position begin = new Position(10, 1);
        final Position end = new Position(11, 2);
        final Range range = new Range(begin, end);
        final Variable variable = new Variable("com.example", "name", range, "com.example.Foo");

        kryoPool.run(new KryoCallback<Variable>() {
            @Override
            public Variable execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, variable);
                    return variable;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final Variable variable1 = kryoPool.run(new KryoCallback<Variable>() {
            @Override
            public Variable execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (Variable) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });
        System.out.println(variable);
        System.out.println(variable1);

        assertEquals(variable1.getRange(), variable.getRange());

    }

}