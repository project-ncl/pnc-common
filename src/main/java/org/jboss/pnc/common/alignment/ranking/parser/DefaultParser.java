package org.jboss.pnc.common.alignment.ranking.parser;

import org.jboss.pnc.common.alignment.ranking.exception.ValidationException;
import org.jboss.pnc.common.alignment.ranking.tokenizer.Token;
import org.jboss.pnc.common.alignment.ranking.tokenizer.TokenType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.pnc.common.alignment.ranking.tokenizer.TokenType.*;

public class DefaultParser implements Parser {
    /**
     * Tokens which are allowed to be at first place
     */
    private static final Set<TokenType> START = new HashSet<>(EnumSet.of(QVALUE, ORDER, LPAREN));

    /**
     * Set of tokens (values) which are allowed to follow a specific Token (key). 'null' signifies that token can be
     * followed by nothing (basically, tokens which can be last)
     *
     * F.e. a Qualifier can be followed by a logic operation(AND, OR, ',') or closing parentheses ')' or SORT_BY clause
     */
    private static final Map<TokenType, Set<TokenType>> ALLOWED_NEXT_TOKENS;

    static {
        ALLOWED_NEXT_TOKENS = new HashMap<>();
        for (TokenType value : TokenType.values()) {
            ALLOWED_NEXT_TOKENS.put(value, new HashSet<>());
            switch (value) {
                case QVALUE:
                    ALLOWED_NEXT_TOKENS.get(QVALUE).addAll(Set.of(LOGIC, COMMA, SORT_BY, RPAREN));
                    ALLOWED_NEXT_TOKENS.get(QVALUE).add(null);
                    break;
                case ORDER:
                    ALLOWED_NEXT_TOKENS.get(ORDER).add(null);
                    break;
                case LOGIC:
                    ALLOWED_NEXT_TOKENS.get(LOGIC).addAll(Set.of(QVALUE, LPAREN));
                    break;
                case LPAREN:
                    ALLOWED_NEXT_TOKENS.get(LPAREN).addAll(Set.of(QVALUE, LPAREN));
                    break;
                case RPAREN:
                    ALLOWED_NEXT_TOKENS.get(RPAREN).addAll(Set.of(LOGIC, COMMA, SORT_BY, RPAREN));
                    ALLOWED_NEXT_TOKENS.get(RPAREN).add(null);
                    break;
                case COMMA:
                    ALLOWED_NEXT_TOKENS.get(RPAREN).addAll(Set.of(QVALUE, LPAREN));
                    break;
                case SORT_BY:
                    ALLOWED_NEXT_TOKENS.get(RPAREN).add(ORDER);
                    break;
            }
        }

    }

    @Override
    public InternalNode generateParseTree(List<Token> tokens) throws ValidationException {

        InternalNode rootNode = new UnaryNode();

        validateInput(tokens);
        createParseTree(rootNode, 0, tokens, 0);

        return rootNode;
    }

    private void validateInput(List<Token> tokens) throws ValidationException {
        if (tokens.isEmpty()) {
            if (!START.contains(null)) {
                throw new ValidationException("Empty list of tokens is not allowed.");
            }
            return;
        }
        for (int i = 0; i < tokens.size(); i++) {
            var currentToken = tokens.get(i);
            var nextType = peekType(i + 1, tokens);

            if (!ALLOWED_NEXT_TOKENS.get(currentToken.tokenType).contains(nextType)) {
                throw new ValidationException(nextType + "is not allowed after " + tokens.get(i), tokens.get(i));
            }
        }
    }

    private TokenType peekType(int toPeek, List<Token> tokens) {
        if (toPeek >= tokens.size()) {
            return null;
        }
        return tokens.get(toPeek).tokenType;
    }

    private void createParseTree(InternalNode currentNode, int tokenIdx, List<Token> tokens, int parenChecker)
            throws ValidationException {
        if (tokenIdx >= tokens.size()) {
            if (parenChecker != 0) {
                throw new ValidationException(
                        "Amount of parentheses do not match. Expecting another " + parenChecker + ".",
                        tokens.get(tokens.size() - 1).endPos);
            }
            return;
        }

        Token token = tokens.get(tokenIdx);
        InternalNode next;
        switch (token.tokenType) {
            case QVALUE:
            case ORDER: {
                addLeaf(currentNode, token);

                next = currentNode;
                break;
            }
            case COMMA:
            case LOGIC: {
                BinaryNode newNode = addBinaryNode(currentNode, token);

                next = newNode;
                break;
            }
            case LPAREN: {
                var parenthesesWrapper = new UnaryNode(token);
                parenChecker++;

                if (currentNode instanceof UnaryNode) {
                    ((UnaryNode) currentNode).setChild(parenthesesWrapper);
                } else if (currentNode instanceof BinaryNode) {
                    ((BinaryNode) currentNode).setRightChild(parenthesesWrapper);
                }

                next = parenthesesWrapper;
                break;
            }
            case RPAREN: {
                if (parenChecker <= 0) {
                    throw new ValidationException("Illegal ')' placement.", token);
                }
                parenChecker--;

                Node node = currentNode.getParent();
                while (!(node instanceof UnaryNode)) {
                    node = node.getParent();
                }
                UnaryNode leftParenNode = (UnaryNode) node;
                InternalNode parenParent = leftParenNode.getParent();
                Node parenChild = leftParenNode.getChild();

                leftParenNode.removeChild(parenChild);
                parenParent.switchChild(leftParenNode, parenChild);

                next = parenParent;
                break;
            }
            case SORT_BY: {
                BinaryNode sortNode = new BinaryNode(token);
                InternalNode rootNode = findRoot(currentNode);
                pushDown((UnaryNode) rootNode, sortNode);

                next = sortNode;
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported token in token list");
        }
        createParseTree(next, ++tokenIdx, tokens, parenChecker);
    }

    private static void addLeaf(InternalNode currentNode, Token token) {
        LeafNode leaf = new LeafNode(token);

        if (currentNode instanceof UnaryNode) {
            ((UnaryNode) currentNode).setChild(leaf);
        } else if (currentNode instanceof BinaryNode) {
            var binary = (BinaryNode) currentNode;
            if (binary.getLeftChild() == null) {
                binary.setLeftChild(leaf);
            }
            if (binary.getRightChild() == null) {
                binary.setRightChild(leaf);
            }
        }
    }

    private BinaryNode addBinaryNode(InternalNode currentNode, Token token) {
        BinaryNode newNode = new BinaryNode(token);
        if (currentNode instanceof UnaryNode) {
            var unary = (UnaryNode) currentNode;

            pushDown(unary, newNode);
        } else if (currentNode instanceof BinaryNode) {
            var currentBinary = (BinaryNode) currentNode;

            if (currentBinary.getPriority() <= newNode.getPriority()) {
                Node rightChild = currentBinary.getRightChild();
                currentBinary.switchChild(rightChild, newNode);

                newNode.setLeftChild(rightChild);
            } else {
                InternalNode currentParent = currentBinary.getParent();
                // for imagination: if currentParent is Binary, currentNode/currentBinary is always rightChild
                currentParent.switchChild(currentBinary, newNode);

                newNode.setLeftChild(currentBinary);
            }
        }
        return newNode;
    }

    private static InternalNode findRoot(InternalNode currentNode) {
        InternalNode node = currentNode;

        while (node.getParent() != null) {
            node = node.getParent();
        }

        return node;
    }

    private void pushDown(UnaryNode parent, BinaryNode node) {
        Node parentChild = parent.getChild();
        parent.switchChild(parentChild, node);

        node.setLeftChild(parentChild);
    }
}
