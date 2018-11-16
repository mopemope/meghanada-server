package meghanada.utils;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.Test;

public class StringUtilsTest {

  @Test
  public void testIsMatchCamelCase() {
    assertTrue(StringUtils.isMatchCamelCase("HashMap", "H"));
    assertTrue(StringUtils.isMatchCamelCase("charAt", "cA"));
    assertFalse(StringUtils.isMatchCamelCase("codePointAt", "cA"));
    assertTrue(StringUtils.isMatchCamelCase("codePointAt", "codPoA"));
    assertTrue(StringUtils.isMatchCamelCase("contains", "con"));
    assertFalse(StringUtils.isMatchCamelCase("istrue", "iT"));
    assertTrue(StringUtils.isMatchCamelCase("isTrue", "isT"));
    assertTrue(StringUtils.isMatchCamelCase("aVeryLongMethodName", "aVLM"));
    assertTrue(StringUtils.isMatchCamelCase("aVeryLongMethodName", "aVLMN"));
    assertTrue(StringUtils.isMatchCamelCase("averylongmethodnameonlylowercase", "averyl"));
    assertTrue(StringUtils.isMatchCamelCase("charAt", ""));
    assertTrue(StringUtils.isMatchCamelCase("acceptJsonFormatterVisitor", "aJFV"));
    assertTrue(StringUtils.isMatchCamelCase("saveOperationChannel", "sOpC"));
    assertTrue(StringUtils.isMatchCamelCase("CHARAT", ""));
    assertTrue(StringUtils.isMatchCamelCase("acceptCase", "a"));
    assertFalse(StringUtils.isMatchCamelCase("StringUtils", "sU"));
    assertTrue(StringUtils.isMatchCamelCase("StringUtils", "SU"));
    assertFalse(StringUtils.isMatchCamelCase("AbstractConsumerConnection", "SU"));
    assertTrue(StringUtils.isMatchCamelCase("appendCodePoint", "aC"));
  }

  @Test
  public void testContains() {
    assertFalse(StringUtils.contains("charAt", "cA"));
    assertFalse(StringUtils.contains("codePointAt", "cA"));
    assertFalse(StringUtils.contains("codePointAt", "codPoA"));
    assertTrue(StringUtils.contains("contains", "con"));
    assertFalse(StringUtils.contains("istrue", "iT"));
    assertTrue(StringUtils.contains("isTrue", "isT"));
    assertFalse(StringUtils.contains("aVeryLongMethodName", "aVLM"));
    assertFalse(StringUtils.contains("aVeryLongMethodName", "aVLMN"));
    assertTrue(StringUtils.contains("averylongmethodnameonlylowercase", "averyl"));
    assertTrue(StringUtils.contains("charAt", ""));
    assertFalse(StringUtils.contains("acceptJsonFormatterVisitor", "aJFV"));
    assertFalse(StringUtils.contains("saveOperationChannel", "sOpC"));
    assertTrue(StringUtils.contains("CHARAT", ""));
    assertTrue(StringUtils.contains("acceptCase", "a"));
    assertFalse(StringUtils.contains("StringUtils", "sU"));
    assertTrue(StringUtils.contains("AbstractConsumerConnection", "SU"));
  }

  @Test
  public void testGetChecksum() throws IOException {
    assertEquals(
        "b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78",
        StringUtils.getChecksum("ABC"));
  }
}
