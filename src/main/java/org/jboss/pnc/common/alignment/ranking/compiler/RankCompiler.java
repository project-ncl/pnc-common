package org.jboss.pnc.common.alignment.ranking.compiler;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jboss.pnc.api.dependencyanalyzer.dto.QualifiedVersion;
import org.jboss.pnc.common.alignment.ranking.parser.BinaryNode;
import org.jboss.pnc.common.alignment.ranking.parser.LeafNode;
import org.jboss.pnc.common.alignment.ranking.parser.Node;
import org.jboss.pnc.common.alignment.ranking.tokenizer.OrderToken;
import org.jboss.pnc.common.alignment.ranking.tokenizer.TokenType;

import java.util.Comparator;

@NoArgsConstructor
@AllArgsConstructor
public class RankCompiler extends AbstractCompiler<Comparator<QualifiedVersion>>
        implements Compiler<Comparator<QualifiedVersion>> {

    @Setter
    private Comparator<String> suffixVersionComparator = Comparator.naturalOrder();

    @Override
    protected Comparator<QualifiedVersion> handleBinaryNode(BinaryNode bNode, TokenType type) {
        switch (type) {
            case LOGIC: {
                return (ver1, ver2) -> evaluateTree(bNode, ver1, ver2);
            }
            case SORT_BY: {
                var rightChild = bNode.getRightChild();
                if (!(rightChild instanceof LeafNode)) {
                    throw new IllegalArgumentException("Order Token leaf node required");
                }

                var left = bNode.getLeftChild();
                var right = (LeafNode) rightChild;

                return (ver1, ver2) -> evaluateWithSort(left, right, ver1, ver2);
            }

            case COMMA: // comma token not allowed in Rankings
            case QVALUE: // can't be binary nodes
            case ORDER:
            case LPAREN:
            case RPAREN:
            default:
                throw new IllegalArgumentException("Binary Node with unusual Token Type: " + bNode.getToken());
        }
    }

    @Override
    protected Comparator<QualifiedVersion> handleLeafNode(LeafNode lNode, TokenType type) {
        switch (type) {
            case QVALUE:
                return (ver1, ver2) -> evaluateTree(lNode, ver1, ver2);
            case ORDER:
                return (ver1, ver2) -> compareByOrder(lNode, ver1, ver2);
            case LOGIC: // can't be leaves
            case COMMA:
            case LPAREN:
            case RPAREN:
            case SORT_BY:
            default:
                throw new IllegalArgumentException("LeafNode of impossible tokenType: " + lNode.getToken());
        }
    }

    private int evaluateWithSort(Node left, LeafNode right, QualifiedVersion ver1, QualifiedVersion ver2) {
        boolean first = match(left, ver1);
        boolean second = match(left, ver2);

        // if both matched, sort by f.e. SUFFIX-VERSION
        if (first && second) {
            return compareByOrder(right, ver1, ver2);
        } else if (!first && !second) {
            return 0;
        } else if (first && !second) {
            return 1;
        } else {
            return -1;
        }
    }

    private int compareByOrder(LeafNode right, QualifiedVersion ver1, QualifiedVersion ver2) {
        OrderToken token = (OrderToken) right.getToken();

        switch (token.order) {
            case SUFFIX_VERSION:
                return Comparator.comparing(QualifiedVersion::getVersion, suffixVersionComparator).compare(ver1, ver2);
            default:
                throw new IllegalArgumentException("Unknown Order Type");
        }
    }

    private int evaluateTree(Node child, QualifiedVersion ver1, QualifiedVersion ver2) {
        boolean first = match(child, ver1);
        boolean second = match(child, ver2);

        if (first == second) {
            return 0;
        } else if (first && !second) {
            return 1;
        } else {
            return -1;
        }
    }
}
