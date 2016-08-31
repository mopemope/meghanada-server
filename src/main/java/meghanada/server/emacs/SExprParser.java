package meghanada.server.emacs;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@SuppressWarnings("unchecked")
class SExprParser {

    private static final Logger log = LogManager.getLogger(SExprParser.class);
    private static final String DOUBLE_QUOTE = "\"";
    private static final String L_PAREN = "(";
    private static final String R_PAREN = ")";
    private static final String ESCAPE = "\\";

    private StringTokenizer tokenizer;

    private StringBuilder sb;
    private boolean escape;
    private Deque<SExprList> currentList = new ArrayDeque<>(2);

    SExprParser() {

    }

    public SExpr parse(final String s) {
        this.tokenizer = new StringTokenizer(s.trim(), " ()\"\\", true);
        this.sb = null;
        return this.get();
    }

    private SExprList getCurrentList() {
        return this.currentList.peek();
    }

    private SExpr get() {
        final String token = this.nextToken();

        if (token.equals(L_PAREN)) {
            final SExprList sexprList = new SExprList();
            this.currentList.push(sexprList);
            getList();
            return this.currentList.pop();
        }

        if (token.equals(DOUBLE_QUOTE)) {
            this.sb = new StringBuilder();
            final SExpr string = getString();
            this.sb = null;
            return string;
        }

        return Atom.makeAtom(token);
    }

    private SExpr getList() {
        final SExprList sexprList = this.getCurrentList();

        if (!this.tokenizer.hasMoreElements()) {
            return null;
        }

        final String token = this.nextToken();

        if (token.equals(R_PAREN)) {
            return sexprList;
        }
        if (token.equals(L_PAREN)) {
            final SExprList nextList = new SExprList();
            this.currentList.push(nextList);
            getList();
            final SExprList inner = this.currentList.pop();
            sexprList.add(inner);
        } else {
            if (token.equals(DOUBLE_QUOTE)) {
                this.sb = new StringBuilder();
                final SExpr string = getString();
                this.sb = null;
                sexprList.add(string);
            } else {
                final SExpr s = Atom.makeAtom(token);
                if (s != null) {
                    sexprList.add(s);
                }
            }
        }
        return getList();
    }

    SExpr getString() {
        final String token = this.nextToken();
        if (token.equals(DOUBLE_QUOTE) && !this.escape) {
            return new AtomString(sb.toString());
        }
        if (token.equals(ESCAPE)) {
            sb.append(token);
            this.escape = true;
        } else {
            sb.append(token);
            this.escape = false;
        }
        return getString();
    }

    private String nextToken() {
        return this.tokenizer.nextToken();
    }

    interface SExpr {

        boolean isAtom();

        int length();

        SExpr get(int i);

        Iterator<SExpr> iterator();

        <T> T value();


    }

    private static class SExprList implements SExpr {

        private List<SExpr> value;

        SExprList() {
            this.value = new ArrayList<>(2);
        }

        void add(SExpr s) {
            this.value.add(s);
        }

        @Override
        public boolean isAtom() {
            return false;
        }

        @Override
        public int length() {
            return this.value.size();
        }

        @Override
        public SExpr get(int i) {
            return this.value.get(i);
        }

        @Override
        public Iterator<SExpr> iterator() {
            return this.value.iterator();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<SExpr> value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }
    }

    static abstract class Atom implements SExpr {

        static Atom makeAtom(String value) {
            log.trace("value {}", value);
            value = value.trim();
            if (value.isEmpty()) {
                return null;
            }
            if (value.matches("^(\\+|-)?\\d+$")) {
                // is integer
                return new AtomInteger(value);
            }
            if (value.matches("^(\\+|-)?\\d+.\\d*$")) {
                // is num
                return new AtomNumber(value);
            }

            if (value.equals("nil")) {
                return new AtomNil();
            }
            if (value.equals("true") || value.equals("t")) {
                return new AtomBoolean(true);
            }
            if (value.equals("false")) {
                return new AtomBoolean(false);
            }
            return new AtomSymbol(value);
        }

        public boolean isAtom() {
            return true;
        }

        @Override
        public int length() {
            return 1;
        }

        @Override
        public SExpr get(int i) {
            throw new UnsupportedOperationException("is atom");
        }

        public Iterator<SExpr> iterator() {
            throw new UnsupportedOperationException("is atom");
        }
    }

    private static class AtomNil extends Atom {

        AtomNil() {
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object value() {
            return null;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", "nil")
                    .toString();
        }

    }

    private static class AtomSymbol extends Atom {

        private final String value;

        AtomSymbol(final String v) {
            this.value = v;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }

    }

    private static class AtomBoolean extends Atom {

        private final Boolean value;

        AtomBoolean(final Boolean v) {
            this.value = v;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Boolean value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }

    }

    private static class AtomInteger extends Atom {

        private final Integer value;

        AtomInteger(final String v) {
            this.value = Integer.parseInt(v);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Integer value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }

    }

    private static class AtomNumber extends Atom {

        private final Float value;

        AtomNumber(final String v) {
            this.value = Float.parseFloat(v);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Float value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }

    }

    private static class AtomString extends Atom {

        private final String value;

        AtomString(final String v) {
            this.value = v;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String value() {
            return this.value;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("value", value)
                    .toString();
        }

    }

}
