package meghanada.server.emacs;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SExpParserTest {

    @Test
    public void testParseInt1() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("10");
        int i = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals(10, i);
    }

    @Test
    public void testParseInt2() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("-10");
        int i = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals(-10, i);
    }

    @Test
    public void testParseF1() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("10.1");
        float i = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals(10.1, i, 0.1);
    }

    @Test
    public void testParseF2() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("-10.102");
        float i = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals(-10.102, i, 0.001);
    }

    @Test
    public void testParseStr1() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("\"TEST\"");
        String s = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals("TEST", s);
    }

    @Test
    public void testParseStr2() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("\"TES T \\\"A\"");
        String s = sexpr.value();
        assertEquals(sexpr.isAtom(), true);
        assertEquals("TES T \\\"A", s);
    }

    @Test
    public void testParseL1() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("()");
        assertEquals(false, sexpr.isAtom());
        assertEquals(0, sexpr.length());
    }

    @Test
    public void testParseL2() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("(10)");
        assertEquals(false, sexpr.isAtom());
        assertEquals(1, sexpr.length());
        int i = sexpr.get(0).value();
        assertEquals(10, i);
    }

    @Test
    public void testParseL3() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("(10 10.0 \"TEST TEST\")");
        assertEquals(false, sexpr.isAtom());
        assertEquals(3, sexpr.length());
        int i = sexpr.get(0).value();
        assertEquals(10, i);
        String s = sexpr.get(2).value();
        assertEquals("TEST TEST", s);
    }

    @Test
    public void testParseL4() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("(10 (10.0 \"TEST TEST\"))");
        assertEquals(false, sexpr.isAtom());
        assertEquals(2, sexpr.length());
        int i = sexpr.get(0).value();
        assertEquals(10, i);
        SExprParser.SExpr lst = sexpr.get(1);
        assertEquals(2, lst.length());
        String s = lst.get(1).value();
        assertEquals("TEST TEST", s);
    }

    @Test
    public void testParseL5() throws Exception {
        final SExprParser sexpParser = new SExprParser();
        final SExprParser.SExpr sexpr = sexpParser.parse("(ping)");
        assertEquals(false, sexpr.isAtom());
        assertEquals(1, sexpr.length());
        List<SExprParser.SExpr> l = sexpr.value();
        System.out.println(l);
        assertEquals(1, l.size());
    }

}