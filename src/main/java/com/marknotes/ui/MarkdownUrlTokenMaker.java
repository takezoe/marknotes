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
    private static final Pattern MARKDOWN_LINK_TARGET_PATTERN = Pattern.compile(
            "^\\(([^\\s)]+)(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\)$"
    );
    private static final Pattern INLINE_LINK_PATTERN = Pattern.compile(
            "(?<!\\\\)\\[[^\\]]*\\]\\(([^\\s)]+)(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\)"
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
            if (current.getType() == TokenTypes.ANNOTATION
                    && prev != null
                    && prev.getLexeme().endsWith("]")) {
                Matcher matcher = MARKDOWN_LINK_TARGET_PATTERN.matcher(current.getLexeme());
                if (matcher.matches()) {
                    Token afterCurrent = current.getNextToken();
                    Token replacement = splitMarkdownLinkTarget((TokenImpl) current, matcher, afterCurrent);
                    ((TokenImpl) prev).setNextToken(replacement);
                    Token last = replacement;
                    while (last.getNextToken() != afterCurrent) {
                        last = last.getNextToken();
                    }
                    prev = last;
                    current = afterCurrent;
                    continue;
                }
            }

            if (current.getType() == TokenTypes.IDENTIFIER) {
                StringBuilder sb = new StringBuilder();
                Token lastMerged = current;
                for (Token t = current; t != null && t.getType() != TokenTypes.NULL; t = t.getNextToken()) {
                    if (t.getType() == TokenTypes.IDENTIFIER ||
                        (t.getType() == TokenTypes.OPERATOR && "~".equals(t.getLexeme()))) {
                        sb.append(t.getLexeme());
                        lastMerged = t;
                    } else {
                        break;
                    }
                }

                String lexeme = sb.toString();
                Matcher linkMatcher = INLINE_LINK_PATTERN.matcher(lexeme);
                if (linkMatcher.find()) {
                    Token afterChain = lastMerged.getNextToken();
                    Token replacement = splitInlineLinkTargets((TokenImpl) current, lexeme, linkMatcher, afterChain);
                    if (prev == null) {
                        head = replacement;
                    } else {
                        ((TokenImpl) prev).setNextToken(replacement);
                    }
                    Token last = replacement;
                    while (last.getNextToken() != afterChain) {
                        last = last.getNextToken();
                    }
                    prev = last;
                    current = afterChain;
                    continue;
                }

                Matcher matcher = URL_PATTERN.matcher(lexeme);
                if (matcher.find()) {
                    Token afterChain = lastMerged.getNextToken();
                    Token replacement = splitToken((TokenImpl) current, lexeme, matcher, afterChain);
                    if (replacement != null) {
                        if (prev == null) {
                            head = replacement;
                        } else {
                            ((TokenImpl) prev).setNextToken(replacement);
                        }
                        Token last = replacement;
                        while (last.getNextToken() != null && last.getNextToken() != afterChain) {
                            last = last.getNextToken();
                        }
                        prev = last;
                        current = last.getNextToken();
                        continue;
                    }
                }
            }
            prev = current;
            current = current.getNextToken();
        }

        return head;
    }

    private Token splitInlineLinkTargets(TokenImpl original, String lexeme, Matcher matcher, Token nextToken) {
        Token head = null;
        Token tail = null;
        int lastEnd = 0;

        do {
            if (matcher.start() > lastEnd) {
                TokenImpl beforeLink = createToken(original, lastEnd, matcher.start(), TokenTypes.IDENTIFIER);
                if (head == null) {
                    head = beforeLink;
                } else {
                    ((TokenImpl) tail).setNextToken(beforeLink);
                }
                tail = beforeLink;
            }

            int labelEnd = lexeme.lastIndexOf(']', matcher.start(1) - 1) + 1;
            TokenImpl label = createToken(original, matcher.start(), labelEnd, TokenTypes.REGEX);
            if (head == null) {
                head = label;
            } else {
                ((TokenImpl) tail).setNextToken(label);
            }
            tail = label;

            TokenImpl openingParenthesis = createToken(original, labelEnd, matcher.start(1), TokenTypes.ANNOTATION);
            ((TokenImpl) tail).setNextToken(openingParenthesis);
            tail = openingParenthesis;

            TokenImpl target = createToken(original, matcher.start(1), matcher.end(1), URL_TOKEN_TYPE);
            ((TokenImpl) tail).setNextToken(target);
            tail = target;

            TokenImpl closingParenthesis = createToken(original, matcher.end(1), matcher.end(), TokenTypes.ANNOTATION);
            ((TokenImpl) tail).setNextToken(closingParenthesis);
            tail = closingParenthesis;
            lastEnd = matcher.end();
        } while (matcher.find());

        if (lastEnd < lexeme.length()) {
            TokenImpl afterTarget = createToken(original, lastEnd, lexeme.length(), TokenTypes.IDENTIFIER);
            ((TokenImpl) tail).setNextToken(afterTarget);
            tail = afterTarget;
        }
        ((TokenImpl) tail).setNextToken(nextToken);
        return head;
    }

    private TokenImpl createToken(TokenImpl original, int start, int end, int type) {
        return new TokenImpl(original.text, original.textOffset + start, original.textOffset + end - 1,
                original.getOffset() + start, type, 0);
    }

    private Token splitMarkdownLinkTarget(TokenImpl original, Matcher matcher, Token nextToken) {
        int targetStart = matcher.start(1);
        int targetEnd = matcher.end(1);
        int textOffset = original.textOffset;
        int docOffset = original.getOffset();

        TokenImpl openingParenthesis = new TokenImpl(original.text,
                textOffset, textOffset + targetStart - 1,
                docOffset, original.getType(), 0);
        TokenImpl target = new TokenImpl(original.text,
                textOffset + targetStart, textOffset + targetEnd - 1,
                docOffset + targetStart, URL_TOKEN_TYPE, 0);
        openingParenthesis.setNextToken(target);

        if (targetEnd < original.getLexeme().length()) {
            TokenImpl closingParenthesis = new TokenImpl(original.text,
                    textOffset + targetEnd, textOffset + original.getLexeme().length() - 1,
                    docOffset + targetEnd, original.getType(), 0);
            target.setNextToken(closingParenthesis);
            closingParenthesis.setNextToken(nextToken);
        } else {
            target.setNextToken(nextToken);
        }
        return openingParenthesis;
    }

    private Token splitToken(TokenImpl original, String lexeme, Matcher matcher, Token nextToken) {
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

        ((TokenImpl) tail).setNextToken(nextToken);
        return head;
    }
}
