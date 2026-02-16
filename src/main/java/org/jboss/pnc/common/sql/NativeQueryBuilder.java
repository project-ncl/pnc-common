package org.jboss.pnc.common.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NativeQueryBuilder {

    private final List<String> selects;
    private final List<String> froms;
    private final List<String> joins;
    private final List<String> wheres;
    private final List<String> orderBys;
    private Integer limit;
    private Integer offset;

    private NativeQueryBuilder() {
        selects = new ArrayList<>();
        froms = new ArrayList<>();
        joins = new ArrayList<>();
        wheres = new ArrayList<>();
        orderBys = new ArrayList<>();
    }

    public NativeQueryBuilder select(String field) {
        selects.add(field);
        return this;
    }

    public NativeQueryBuilder select(String table, String field) {
        selects.add((table == null || table.isEmpty() ? "" : table + '.') + field);
        return this;
    }

    public NativeQueryBuilder select(String table, String field, String alias) {
        selects.add(
                (table == null || table.isEmpty() ? "" : table + '.') + field
                        + (alias == null || alias.isEmpty() ? "" : " AS " + alias));
        return this;
    }

    public NativeQueryBuilder requiresSelect(String table, String field, String alias) {
        String selectClause = (table == null || table.isEmpty() ? "" : table + '.') + field
                + (alias == null || alias.isEmpty() ? "" : " AS " + alias);
        if (!selects.contains(selectClause))
            selects.add(selectClause);
        return this;
    }

    public NativeQueryBuilder from(String table) {
        froms.add(table);
        return this;
    }

    public NativeQueryBuilder from(String table, String alias) {
        froms.add(table + (alias == null || alias.isEmpty() ? "" : " AS " + alias));
        return this;
    }

    public NativeQueryBuilder join(String joinType, String table, String alias, String onClause) {
        joins.add(
                joinType + " JOIN " + table + (alias == null || alias.isEmpty() ? "" : " AS " + alias)
                        + (onClause == null || onClause.isBlank() ? "" : " ON " + onClause));
        return this;
    }

    public NativeQueryBuilder requiresJoin(String joinType, String table, String alias, String onClause) {
        String joinClause = joinType + " JOIN " + table + (alias == null || alias.isEmpty() ? "" : " AS " + alias)
                + (onClause == null || onClause.isBlank() ? "" : " ON " + onClause);

        if (!joins.contains(joinClause))
            joins.add(joinClause);

        return this;
    }

    public NativeQueryBuilder where(String condition) {
        wheres.add(condition);
        return this;
    }

    public NativeQueryBuilder orderBy(String... orderFields) {
        orderBys.addAll(Arrays.asList(orderFields));
        return this;
    }

    public NativeQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public NativeQueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public static NativeQueryBuilder builder() {
        return new NativeQueryBuilder();
    }

    public String build() {
        return toString();
    }

    @Override
    public String toString() {
        StringBuilder queryString = new StringBuilder();

        if (!selects.isEmpty()) {
            queryString.append("SELECT ").append(String.join(", ", selects));
        }
        if (!froms.isEmpty()) {
            queryString.append(" FROM ").append(String.join(", ", froms));
        }
        if (!joins.isEmpty()) {
            queryString.append(" ").append(String.join(" ", joins));
        }
        if (!wheres.isEmpty()) {
            queryString.append(" WHERE ").append(String.join(" AND ", wheres));
        }
        if (!orderBys.isEmpty()) {
            queryString.append(" ORDER BY ").append(String.join(", ", orderBys));
        }
        if (limit != null) {
            queryString.append(" LIMIT ").append(limit);
        }
        if (offset != null) {
            queryString.append(" OFFSET ").append(offset);
        }

        return queryString.toString();
    }
}
