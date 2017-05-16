package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Context;

public class FuzzyParserFactory extends ParserFactory {

  protected FuzzyParserFactory(Context context) {
    super(context);
  }

  public static FuzzyParserFactory instance(Context context) {
    FuzzyParserFactory instance = (FuzzyParserFactory) context.get(parserFactoryKey);
    if (instance == null) {
      instance = new FuzzyParserFactory(context);
    }
    return instance;
  }

  @Override
  public JavacParser newParser(
      CharSequence input, boolean keepDocComments, boolean keepEndPos, boolean keepLineMap) {
    Lexer lexer = scannerFactory.newScanner(input, keepDocComments);

    return new FuzzyParser(this, lexer, keepDocComments, keepLineMap, keepEndPos);
  }
}
