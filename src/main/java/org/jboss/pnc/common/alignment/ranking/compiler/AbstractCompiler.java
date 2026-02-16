package org.jboss.pnc.common.alignment.ranking.compiler;

import org.jboss.pnc.api.dependencyanalyzer.dto.QualifiedVersion;
import org.jboss.pnc.common.alignment.ranking.exception.ValidationException;
import org.jboss.pnc.common.alignment.ranking.parser.BinaryNode;
import org.jboss.pnc.common.alignment.ranking.parser.InternalNode;
import org.jboss.pnc.common.alignment.ranking.parser.LeafNode;
import org.jboss.pnc.common.alignment.ranking.parser.Node;
import org.jboss.pnc.common.alignment.ranking.tokenizer.LogicToken;
import org.jboss.pnc.common.alignment.ranking.tokenizer.QualifierToken;
import org.jboss.pnc.common.alignment.ranking.tokenizer.TokenType;

import static java.text.MessageFormat.format;

public abstract class AbstractCompiler<T> implements Compiler<T> {

    protected abstract T handleBinaryNode(BinaryNode bNode, TokenType type);

    protected abstract T handleLeafNode(LeafNode bNode, TokenType type);

    @Override
    public T compile(InternalNode rootNode) {
        if (rootNode.childrenCount() != 1) {
            throw new ValidationException("Empty rank is not allowed.");
        }

        var child = rootNode.getChildren().get(0);
        if (child instanceof BinaryNode) {
            BinaryNode bNode = (BinaryNode) child;

            return handleBinaryNode(bNode, bNode.getToken().tokenType);
        } else if (child instanceof LeafNode) {
            LeafNode lNode = (LeafNode) child;

            return handleLeafNode(lNode, lNode.getToken().tokenType);
        }

        // UNREACHABLE
        throw new IllegalArgumentException("Unknown Node Type: " + child.getClass().getCanonicalName());
    }

    protected boolean match(Node currentNode, QualifiedVersion version) {
        if (currentNode instanceof LeafNode) {
            var leaf = (LeafNode) currentNode;
            if (leaf.getToken().tokenType != TokenType.QVALUE) {
                throw new IllegalArgumentException("Unknown leaf token. Only Qualifier:Value pairs allowed.");
            }

            var token = (QualifierToken) leaf.getToken();

            return version.has(token.qualifier, token.parts);
        } else if (currentNode instanceof BinaryNode) {
            var bNode = (BinaryNode) currentNode;
            switch (bNode.getToken().tokenType) {
                case LOGIC:
                    var token = (LogicToken) bNode.getToken();

                    switch (token.logicType) {
                        case AND:
                            return handleAnd(bNode, version);
                        case OR:
                            return handleOr(bNode, version);
                        default:
                            throw new IllegalArgumentException(format("Unknown logic operation: {0}", token.logicType));
                    }
                case COMMA: // in Predicate compilers comma signifies OR
                    return handleOr(bNode, version);
                default:
                    throw new IllegalArgumentException(format("Unknown BinaryNode type {0}", bNode.getToken()));
            }

        }
        return false;
    }

    private boolean handleAnd(BinaryNode currentNode, QualifiedVersion version) {
        boolean leftMatch = match(currentNode.getLeftChild(), version);
        if (!leftMatch) {
            // AND optimization, no need to execute the right tree
            return false;
        }

        boolean rightMatch = match(currentNode.getRightChild(), version);

        return leftMatch && rightMatch;
    }

    private boolean handleOr(BinaryNode currentNode, QualifiedVersion version) {
        boolean leftMatch = match(currentNode.getLeftChild(), version);
        if (leftMatch) {
            // OR optimization, no need to execute the right tree
            return true;
        }

        boolean rightMatch = match(currentNode.getRightChild(), version);

        return leftMatch || rightMatch;
    }
}
