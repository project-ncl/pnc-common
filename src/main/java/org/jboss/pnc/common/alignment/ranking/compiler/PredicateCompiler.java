package org.jboss.pnc.common.alignment.ranking.compiler;

import org.jboss.pnc.api.dependencyanalyzer.dto.QualifiedVersion;
import org.jboss.pnc.common.alignment.ranking.parser.BinaryNode;
import org.jboss.pnc.common.alignment.ranking.parser.LeafNode;
import org.jboss.pnc.common.alignment.ranking.tokenizer.TokenType;

import java.util.function.Predicate;

public class PredicateCompiler extends AbstractCompiler<Predicate<QualifiedVersion>>
        implements Compiler<Predicate<QualifiedVersion>> {

    @Override
    protected Predicate<QualifiedVersion> handleBinaryNode(BinaryNode bNode, TokenType type) {
        switch (bNode.getToken().tokenType) {
            case COMMA:
            case LOGIC: {
                return (ver) -> match(bNode, ver);
            }
            case SORT_BY:
            default:
                throw new IllegalArgumentException("Binary Node with unusual Token Type: " + bNode.getToken());
        }
    }

    @Override
    protected Predicate<QualifiedVersion> handleLeafNode(LeafNode lNode, TokenType type) {
        switch (lNode.getToken().tokenType) {
            case QVALUE:
                return (ver) -> match(lNode, ver);
            case ORDER:
            case LOGIC:
            case COMMA:
            case LPAREN:
            case RPAREN:
            case SORT_BY:
            default:
                throw new IllegalArgumentException("LeafNode of impossible tokenType: " + lNode.getToken());
        }
    }
}
