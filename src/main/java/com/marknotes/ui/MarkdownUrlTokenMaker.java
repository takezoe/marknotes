package com.marknotes.ui;

import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenImpl;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rsyntaxtextarea.modes.MarkdownTokenMaker;

import javax.swing.text.Segment;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownUrlTokenMaker extends MarkdownTokenMaker {

    public static final int URL_TOKEN_TYPE = TokenTypes.LITERAL_STRING_DOUBLE_QUOTE;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|ftp://|www\\.)[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*[A-Za-z0-9/\\-_~)\\]=%#]"
    );

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        Token tokens = super.getTokenList(text, initialTokenType, startOffset);
        return processTokens(tokens);
    }

    private Token processTokens(Token tokens) {
        Token current = tokens;
        Token prev = null;
        Token head = tokens;

        while (current != null && current.getType() != TokenTypes.NULL) {
            if (current.getType() == TokenTypes.IDENTIFIER) {
                String lexeme = current.getLexeme();
                if (lexeme != null) {
                    Matcher matcher = URL_PATTERN.matcher(lexeme);
                    if (matcher.find()) {
                        Token replacement = splitToken((TokenImpl) current, lexeme, matcher);
                        if (replacement != null) {
                            if (prev == null) {
                                head = replacement;
                            } else {
                                ((TokenImpl) prev).setNextToken(replacement);
                            }
                            // advance to end of replacement chain
                            Token last = replacement;
                            while (last.getNextToken() != null && last.getNextToken() != current.getNextToken()) {
                                last = last.getNextToken();
                            }
                            prev = last;
                            current = last.getNextToken();
                            continue;
                        }
                    }
                }
            }
            prev = current;
            current = current.getNextToken();
        }

        return head;
    }

    private Token splitToken(TokenImpl original, String lexeme, Matcher matcher) {
        int urlStart = matcher.start();
        int urlEnd = matcher.end();

        char[] textArray = original.text;
        int textOffset = original.textOffset;
        int docOffset = original.getOffset();

        Token head = null;
        Token tail = null;

        if (urlStart > 0) {
            TokenImpl before = new TokenImpl(textArray,
                    textOffset, textOffset + urlStart - 1,
                    docOffset, TokenTypes.IDENTIFIER, 0);
            head = before;
            tail = before;
        }

        TokenImpl urlToken = new TokenImpl(textArray,
                textOffset + urlStart, textOffset + urlEnd - 1,
                docOffset + urlStart, URL_TOKEN_TYPE, 0);
        if (head == null) {
            head = urlToken;
        } else {
            ((TokenImpl) tail).setNextToken(urlToken);
        }
        tail = urlToken;

        if (urlEnd < lexeme.length()) {
            TokenImpl after = new TokenImpl(textArray,
                    textOffset + urlEnd, textOffset + lexeme.length() - 1,
                    docOffset + urlEnd, TokenTypes.IDENTIFIER, 0);
            ((TokenImpl) tail).setNextToken(after);
            tail = after;
        }

        ((TokenImpl) tail).setNextToken(original.getNextToken());
        return head;
    }
}
