package com.functionlib.db;

import com.functionlib.FunctionlibApp.BookEntry;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookDao {

    public static void insertOrUpdate(File dbFolder, BookEntry book) throws SQLException {
        String sql = "MERGE INTO books KEY(path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(dbFolder);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, book.getFile().getAbsolutePath().replace("\\", "/"));
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getGeneration());
            ps.setInt(5, book.getPages());
            ps.setInt(6, book.getWordCount());
            ps.setString(7, book.getContentHash());
            ps.setLong(8, book.getFile().lastModified());
            ps.executeUpdate();
        }
    }

public static int delete(File dbFolder, String filePath) throws SQLException {
    String sql = "DELETE FROM books WHERE path = ?";
    try (Connection conn = DatabaseManager.getConnection(dbFolder);
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, filePath.replace("\\", "/"));
        return ps.executeUpdate();
    }
}

    public static List<BookEntry> loadAll(File dbFolder) throws SQLException {
        String sql = "SELECT * FROM books WHERE path LIKE ?";
        String prefix = dbFolder.getAbsolutePath().replace("\\", "/") + "/%";
        try (Connection conn = DatabaseManager.getConnection(dbFolder);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix);
            ResultSet rs = ps.executeQuery();
            List<BookEntry> list = new ArrayList<>();
            while (rs.next()) {
                File file = new File(rs.getString("path"));
                BookEntry book = new BookEntry(
                        file.getName(),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("generation"),
                        file,
                        rs.getInt("page_count"),
                        rs.getInt("word_count"),
                        rs.getString("content_hash")
                );
                list.add(book);
            }
            return list;
        }
    }

    public static boolean isDatabaseEmpty(File dbFolder) throws SQLException {
    String sql = "SELECT COUNT(*) FROM books";
    try (Connection conn = DatabaseManager.getConnection(dbFolder);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
        return rs.next() && rs.getInt(1) == 0;
    } catch (SQLException e) {
        // 如果表不存在，视为空
        if (e.getMessage().contains("not found")) {
            return true;
        }
        throw e;
    }
}
}