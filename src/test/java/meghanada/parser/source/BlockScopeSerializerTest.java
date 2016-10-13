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

public class BlockScopeSerializerTest extends GradleTestBase {

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
        final BlockScope blockScope = new BlockScope("scope", range);
        System.out.println(blockScope);

        kryoPool.run(new KryoCallback<BlockScope>() {
            @Override
            public BlockScope execute(Kryo kryo) {
                try (final Output out = new Output(new FileOutputStream(tempFile))) {
                    kryo.writeClassAndObject(out, blockScope);
                    return blockScope;
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        final BlockScope blockScope1 = kryoPool.run(new KryoCallback<BlockScope>() {
            @Override
            public BlockScope execute(Kryo kryo) {
                try (final Input input = new Input(new FileInputStream(tempFile))) {
                    return (BlockScope) kryo.readClassAndObject(input);
                } catch (FileNotFoundException e) {
                    fail(e.getMessage());
                }
                return null;
            }
        });

        System.out.println(blockScope1);

        assertEquals(blockScope1.getName(), blockScope.getName());
        assertEquals(blockScope1.getRange(), blockScope.getRange());

    }


}
