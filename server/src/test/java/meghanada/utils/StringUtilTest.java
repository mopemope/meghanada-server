package meghanada.utils;

import org.junit.Test;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.base.Splitter;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Author: Xiang Qian Liu(xqianliu@cn.ibm.com)
 * Date: 2018/5/14
 */
public class StringUtilTest {

    @Test
    public void testIsMatch() {
        assertTrue(StringUtils.getInstance().isMatch("charAt", "cA"));
        assertFalse(StringUtils.getInstance().isMatch("codePointAt", "cA"));
        assertTrue(StringUtils.getInstance().isMatch("codePointAt", "codPoA"));
        assertTrue(StringUtils.getInstance().isMatch("contains", "con"));
        assertFalse(StringUtils.getInstance().isMatch("istrue", "iT"));
        assertTrue(StringUtils.getInstance().isMatch("isTrue", "isT"));
        assertTrue(StringUtils.getInstance().isMatch("aVeryLongMethodName", "aVLM"));
        assertTrue(StringUtils.getInstance().isMatch("aVeryLongMethodName", "aVLMN"));
        assertTrue(StringUtils.getInstance().isMatch("averylongmethodnameonlylowercase", "averyl"));
        assertTrue(StringUtils.getInstance().isMatch("charAt", ""));
        assertTrue(StringUtils.getInstance().isMatch("acceptJsonFormatterVisitor", "aJFV"));
        assertTrue(StringUtils.getInstance().isMatch("saveOperationChannel", "sOpC"));
        assertTrue(StringUtils.getInstance().isMatch("CHARAT", ""));
        assertTrue(StringUtils.getInstance().isMatch("acceptCase", "a"));
        assertTrue(StringUtils.getInstance().isMatch("acceptCase", "a"));
    }
}
