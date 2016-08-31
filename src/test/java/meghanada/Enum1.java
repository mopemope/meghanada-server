package meghanada;

public class Enum1 {

    public enum KeyType {

        ZEROZERO("00", null) {
            @Override
            public int calc(int old) {
                String newVal = old + getKeyValue();
                return Integer.valueOf(newVal);
            }

        },
        ZERO("0", null) {
            @Override
            public int calc(int old) {
                return ZEROZERO.calc(old);
            }

        },
        ONE("1", null), TWO("2", null), THREE("3", null), FOUR("4", null),
        FIVE("5", null), SIX("6", null), SEVEN("7", null), EIGHT("8", null), NINE("9", null),
        DELETE("delete", "ic_backspace") {
            @Override
            public int calc(int old) {
                return 0;
            }

        };

        private final String regionName;
        private final String number;

        KeyType(String number, String regionName) {
            this.number = number;
            this.regionName = regionName;
        }

        public boolean isText() {
            return regionName == null;
        }

        public int calc(int old) {
            String sOld = String.valueOf(old);
            if (sOld.equals("0")) {
                return Integer.valueOf(getKeyValue());
            }

            String newVal = old + getKeyValue();
            return Integer.valueOf(newVal);
        }


        public String getKeyValue() {
            return number;
        }
    }

}
