package com.redis.smartcache.jdbc;

import java.sql.JDBCType;
import java.sql.SQLException;

import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.redis.smartcache.jdbc.rowset.CachedRowSetImpl;
import com.redis.smartcache.test.RowSetBuilder;

class CachedRowSetTests {

    private final RowSetFactory rowSetFactory = new RowSetFactoryImpl();

    @Test
    void populate() throws SQLException {
        RowSetBuilder builder = RowSetBuilder.of(rowSetFactory);
        RowSet rowSet = builder.build();
        CachedRowSetImpl actual = new CachedRowSetImpl();
        actual.populate(rowSet);
        rowSet.beforeFirst();
        Utils.assertEquals(rowSet, actual);
    }

    @Test
    void getInt() throws SQLException {
        RowSetBuilder builder = RowSetBuilder.of(rowSetFactory);
        builder.columns(JDBCType.NUMERIC);
        double value = 123.0;
        builder.columnUpdater((r, i) -> r.updateObject(i, value));
        CachedRowSet rowSet = builder.build();
        rowSet.next();
        Assertions.assertEquals(value, rowSet.getInt(1));
    }

}
