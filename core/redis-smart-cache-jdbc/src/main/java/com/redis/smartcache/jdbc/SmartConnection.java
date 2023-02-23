package com.redis.smartcache.jdbc;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.zip.CRC32;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;

import com.redis.smartcache.core.Query;
import com.redis.smartcache.core.QueryRuleSession;
import com.redis.smartcache.core.ResultSetCache;
import com.redis.smartcache.core.util.EvictingLinkedHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.trino.sql.parser.ParsingException;
import io.trino.sql.parser.ParsingOptions;
import io.trino.sql.parser.SqlParser;

public class SmartConnection implements Connection {

	private static final String METER_PREFIX_CACHE = "cache";
	private static final String METER_QUERY = "query";
	private static final String METER_BACKEND = "backend";
	private static final String METER_CACHE_GET = METER_PREFIX_CACHE + ".get";
	private static final String METER_CACHE_PUT = METER_PREFIX_CACHE + ".put";
	private static final String TAG_RESULT = "result";
	private static final String TAG_MISS = "miss";
	private static final String TAG_HIT = "hit";
	private static final String TAG_QUERY = "query";
	private static final ParsingOptions PARSING_OPTIONS = new ParsingOptions();

	private final SqlParser parser = new SqlParser();
	private final Map<String, Query> queryCache;

	private final Connection connection;
	private final QueryRuleSession ruleSession;
	private final RowSetFactory rowSetFactory;
	private final ResultSetCache resultSetCache;
	private final MeterRegistry meterRegistry;

	public SmartConnection(Connection connection, QueryRuleSession ruleSession, RowSetFactory rowSetFactory,
			ResultSetCache resultSetCache, MeterRegistry meterRegistry, int queryCacheCapacity) {
		this.connection = connection;
		this.ruleSession = ruleSession;
		this.rowSetFactory = rowSetFactory;
		this.resultSetCache = resultSetCache;
		this.meterRegistry = meterRegistry;
		this.queryCache = new EvictingLinkedHashMap<>(queryCacheCapacity);
	}

	public ResultSetCache getResultSetCache() {
		return resultSetCache;
	}

	@Override
	public void close() throws SQLException {
		connection.close();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return connection.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return connection.isWrapperFor(iface);
	}

	@Override
	public Statement createStatement() throws SQLException {
		Statement statement = connection.createStatement();
		return new SmartStatement(this, statement);
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		CallableStatement statement = connection.prepareCall(sql);
		return new SmartCallableStatement(this, statement, sql);
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		return connection.nativeSQL(sql);
	}

	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		connection.setAutoCommit(autoCommit);
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		return connection.getAutoCommit();
	}

	@Override
	public void commit() throws SQLException {
		connection.commit();
	}

	@Override
	public void rollback() throws SQLException {
		connection.rollback();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return connection.isClosed();
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return connection.getMetaData();
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		connection.setReadOnly(readOnly);
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		return connection.isReadOnly();
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		connection.setCatalog(catalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		return connection.getCatalog();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		connection.setTransactionIsolation(level);
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		return connection.getTransactionIsolation();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return connection.getWarnings();
	}

	@Override
	public void clearWarnings() throws SQLException {
		connection.clearWarnings();
	}

	@Override
	public Statement createStatement(int rsType, int rsConcurrency) throws SQLException {
		return new SmartStatement(this, connection.createStatement(rsType, rsConcurrency));
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int rsType, int rsConcurrency) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql, rsType, rsConcurrency);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql, int rsType, int rsConcurrency) throws SQLException {
		CallableStatement statement = connection.prepareCall(sql, rsType, rsConcurrency);
		return new SmartCallableStatement(this, statement, sql);
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return connection.getTypeMap();
	}

	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		connection.setTypeMap(map);
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		connection.setHoldability(holdability);
	}

	@Override
	public int getHoldability() throws SQLException {
		return connection.getHoldability();
	}

	@Override
	public Savepoint setSavepoint() throws SQLException {
		return connection.setSavepoint();
	}

	@Override
	public Savepoint setSavepoint(String name) throws SQLException {
		return connection.setSavepoint(name);
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		connection.rollback(savepoint);
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		connection.releaseSavepoint(savepoint);
	}

	@Override
	public Statement createStatement(int rsType, int rsConcurrency, int rsHoldability) throws SQLException {
		Statement statement = connection.createStatement(rsType, rsConcurrency, rsHoldability);
		return new SmartStatement(this, statement);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int rsType, int rsConcurrency, int rsHoldability)
			throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql, rsType, rsConcurrency, rsHoldability);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public CallableStatement prepareCall(String sql, int rsType, int rsConcurrency, int rsHoldability)
			throws SQLException {
		CallableStatement statement = connection.prepareCall(sql, rsType, rsConcurrency, rsHoldability);
		return new SmartCallableStatement(this, statement, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql, autoGeneratedKeys);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql, columnIndexes);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql, columnNames);
		return new SmartPreparedStatement(this, statement, sql);
	}

	@Override
	public Clob createClob() throws SQLException {
		return connection.createClob();
	}

	@Override
	public Blob createBlob() throws SQLException {
		return connection.createBlob();
	}

	@Override
	public NClob createNClob() throws SQLException {
		return connection.createNClob();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		return connection.createSQLXML();
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		return connection.isValid(timeout);
	}

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		connection.setClientInfo(name, value);
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		connection.setClientInfo(properties);
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		return connection.getClientInfo(name);
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		return connection.getClientInfo();
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		return connection.createArrayOf(typeName, elements);
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		return connection.createStruct(typeName, attributes);
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		connection.setSchema(schema);
	}

	@Override
	public String getSchema() throws SQLException {
		return connection.getSchema();
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		connection.abort(executor);
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		connection.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		return connection.getNetworkTimeout();
	}

	public Query getQuery(String sql) {
		String id = crc32(sql);
		if (queryCache.containsKey(id)) {
			return queryCache.get(id);
		}
		return new Query(id, sql, parse(sql), timer(METER_QUERY, id), timer(METER_BACKEND, id),
				timer(METER_CACHE_GET, id), timer(METER_CACHE_PUT, id),
				counter(METER_CACHE_GET, id, TAG_RESULT, TAG_HIT), counter(METER_CACHE_GET, id, TAG_RESULT, TAG_MISS));
	}

	private Timer timer(String name, String queryId) {
		return Timer.builder(name).tag(TAG_QUERY, queryId).publishPercentiles(0.9, 0.99).register(meterRegistry);
	}

	private Counter counter(String name, String queryId, String... tags) {
		return Counter.builder(name).tag(TAG_QUERY, queryId).tags(tags).register(meterRegistry);
	}

	private io.trino.sql.tree.Statement parse(String sql) {
		try {
			return parser.createStatement(sql, PARSING_OPTIONS);
		} catch (ParsingException e) {
			// This statement cannot be parsed. Only rules like regex can trigger
			return null;
		}

	}

	public static String crc32(String string) {
		CRC32 crc = new CRC32();
		crc.update(string.getBytes(StandardCharsets.UTF_8));
		return String.valueOf(crc.getValue());
	}

	public Query fireRules(String sql) {
		Query query = getQuery(sql);
		ruleSession.fire(query);
		return query;
	}

	public CachedRowSet createCachedRowSet() throws SQLException {
		return rowSetFactory.createCachedRowSet();
	}

}
