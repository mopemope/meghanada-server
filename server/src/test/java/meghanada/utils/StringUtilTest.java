package meghanada.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class StringUtilTest {

  @Test
  public void testIsMatchWhenConfigOn() {
    StringUtils.getInstance().setUseccc(true);
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
    assertFalse(StringUtils.getInstance().isMatch("StringUtils", "sU"));
    assertTrue(StringUtils.getInstance().isMatch("StringUtils", "SU"));
    assertFalse(StringUtils.getInstance().isMatch("AbstractConsumerConnection", "SU"));
    assertTrue(StringUtils.getInstance().isMatch("appendCodePoint", "aC"));
  }

  @Test
  public void testWhenConfigOff() {
    StringUtils.getInstance().setUseccc(false);
    assertFalse(StringUtils.getInstance().isMatch("charAt", "cA"));
    assertFalse(StringUtils.getInstance().isMatch("codePointAt", "cA"));
    assertFalse(StringUtils.getInstance().isMatch("codePointAt", "codPoA"));
    assertTrue(StringUtils.getInstance().isMatch("contains", "con"));
    assertFalse(StringUtils.getInstance().isMatch("istrue", "iT"));
    assertTrue(StringUtils.getInstance().isMatch("isTrue", "isT"));
    assertFalse(StringUtils.getInstance().isMatch("aVeryLongMethodName", "aVLM"));
    assertFalse(StringUtils.getInstance().isMatch("aVeryLongMethodName", "aVLMN"));
    assertTrue(StringUtils.getInstance().isMatch("averylongmethodnameonlylowercase", "averyl"));
    assertTrue(StringUtils.getInstance().isMatch("charAt", ""));
    assertFalse(StringUtils.getInstance().isMatch("acceptJsonFormatterVisitor", "aJFV"));
    assertFalse(StringUtils.getInstance().isMatch("saveOperationChannel", "sOpC"));
    assertTrue(StringUtils.getInstance().isMatch("CHARAT", ""));
    assertTrue(StringUtils.getInstance().isMatch("acceptCase", "a"));
    assertFalse(StringUtils.getInstance().isMatch("StringUtils", "sU"));
    assertTrue(StringUtils.getInstance().isMatch("AbstractConsumerConnection", "SU"));
  }
}
