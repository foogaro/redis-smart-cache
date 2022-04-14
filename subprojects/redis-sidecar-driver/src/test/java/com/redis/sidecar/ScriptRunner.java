package com.redis.sidecar;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool to run database scripts
 */
public class ScriptRunner {

	private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

	private static final String DEFAULT_DELIMITER = ";";

	private final Connection connection;
	private boolean stopOnError;
	private boolean autoCommit;
	private String delimiter = DEFAULT_DELIMITER;
	private boolean fullLineDelimiter;

	public ScriptRunner(Connection connection) {
		this.connection = connection;
	}

	public void setStopOnError(boolean stopOnError) {
		this.stopOnError = stopOnError;
	}

	public void setAutoCommit(boolean autoCommit) {
		this.autoCommit = autoCommit;
	}

	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	public void setFullLineDelimiter(boolean fullLineDelimiter) {
		this.fullLineDelimiter = fullLineDelimiter;
	}

	/**
	 * Runs an SQL script (read in using the Reader parameter)
	 *
	 * @param reader - the source of the script
	 */
	public void runScript(Reader reader) throws IOException, SQLException {
		boolean originalAutoCommit = connection.getAutoCommit();
		try {
			if (originalAutoCommit != this.autoCommit) {
				connection.setAutoCommit(this.autoCommit);
			}
			runScript(connection, reader);
		} finally {
			connection.setAutoCommit(originalAutoCommit);
		}
	}

	/**
	 * Runs an SQL script (read in using the Reader parameter) using the connection
	 * passed in
	 *
	 * @param conn   - the connection to use for the script
	 * @param reader - the source of the script
	 * @throws SQLException if any SQL errors occur
	 * @throws IOException  if there is an error reading from the Reader
	 */
	@SuppressWarnings("unused")
	private void runScript(Connection conn, Reader reader) throws IOException, SQLException {
		StringBuffer command = null;
		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;
		while ((line = lineReader.readLine()) != null) {
			if (command == null) {
				command = new StringBuffer();
			}
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith("--")) {
				log.debug(trimmedLine);
			} else if (trimmedLine.length() < 1 || trimmedLine.startsWith("//")) {
				// Do nothing
			} else if (trimmedLine.length() < 1 || trimmedLine.startsWith("--")) {
				// Do nothing
			} else if (!fullLineDelimiter && trimmedLine.endsWith(delimiter)
					|| fullLineDelimiter && trimmedLine.equals(delimiter)) {
				command.append(line, 0, line.lastIndexOf(delimiter));
				command.append(" ");
				Statement statement = conn.createStatement();

				log.debug(command.toString());

				boolean hasResults = false;
				if (stopOnError) {
					hasResults = statement.execute(command.toString());
				} else {
					try {
						statement.execute(command.toString());
					} catch (SQLException e) {
						log.error("Error executing: {}", command, e);
					}
				}

				if (autoCommit && !conn.getAutoCommit()) {
					conn.commit();
				}

				ResultSet rs = statement.getResultSet();
				if (hasResults && rs != null) {
					ResultSetMetaData md = rs.getMetaData();
					int cols = md.getColumnCount();
					for (int i = 0; i < cols; i++) {
						String name = md.getColumnLabel(i);
					}
					while (rs.next()) {
						for (int i = 0; i < cols; i++) {
							String value = rs.getString(i);
						}
					}
				}

				command = null;
				try {
					statement.close();
				} catch (Exception e) {
					// Ignore to workaround a bug in Jakarta DBCP
				}
				Thread.yield();
			} else {
				command.append(line);
				command.append(" ");
			}
		}
		if (!autoCommit) {
			conn.commit();
		}
	}

}