package com.redis.smartcache;

import java.sql.JDBCType;
import java.sql.SQLException;

import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.redis.smartcache.core.rowset.CachedRowSetFactory;
import com.redis.smartcache.core.rowset.CachedRowSetImpl;
import com.redis.smartcache.test.RowSetBuilder;

class CachedRowSetTests {

	@Test
	void populate() throws SQLException {
		RowSetBuilder builder = RowSetBuilder.of(new CachedRowSetFactory());
		RowSet rowSet = builder.build();
		CachedRowSetImpl actual = new CachedRowSetImpl();
		actual.populate(rowSet);
		rowSet.beforeFirst();
		TestUtils.assertEquals(rowSet, actual);
	}

	@Test
	void getInt() throws SQLException {
		RowSetBuilder builder = RowSetBuilder.of(new CachedRowSetFactory());
		builder.columns(JDBCType.NUMERIC);
		double value = 123.0;
		builder.columnUpdater((r, i) -> r.updateObject(i, value));
		CachedRowSet rowSet = builder.build();
		rowSet.next();
		Assertions.assertEquals(value, rowSet.getInt(1));
	}

}
