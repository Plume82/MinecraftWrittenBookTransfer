package com.functionlib.db;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private static final String DB_FILE_NAME = ".dbbook";  // ✅ 定义常量

    public static Connection getConnection(File dbFolder) throws SQLException {
        if (dbFolder == null || !dbFolder.isDirectory()) {
            throw new IllegalArgumentException("dbFolder 必须是存在的目录");
        }

        File dbFile = new File(dbFolder, DB_FILE_NAME);
        String url = "jdbc:h2:file:" + dbFile.getAbsolutePath() + ";MODE=MySQL;DATABASE_TO_UPPER=false";
        Connection conn = DriverManager.getConnection(url, "sa", "");
        createTablesIfNeeded(conn);
        return conn;
    }

    private static void createTablesIfNeeded(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS books (" +
                "path VARCHAR(512) PRIMARY KEY, " +
                "title VARCHAR(255), " +
                "author VARCHAR(100), " +
                "generation VARCHAR(20), " +
                "page_count INT, " +
                "word_count INT, " +
                "content_hash VARCHAR(64), " +
                "last_modified BIGINT" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }
}