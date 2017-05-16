package com.sun.tools.javac.parser;

import static com.sun.tools.javac.parser.Tokens.TokenKind.SEMI;

public class FuzzyParser extends JavacParser {

  protected FuzzyParser(
      FuzzyParserFactory parserFactory, Lexer lexer, boolean b, boolean b1, boolean b2) {
    super(parserFactory, lexer, b, b1, b2);
  }

  @Override
  public void accept(Tokens.TokenKind tk) {
    if (token.kind == tk) {
      nextToken();
    } else if (tk == SEMI) {
    } else {
      super.accept(tk);
    }
  }
}
