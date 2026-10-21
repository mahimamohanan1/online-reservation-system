package com.railease.desktop;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class Database {
    private static final String URL = "jdbc:sqlite:railease-desktop.db";
    private static final SecureRandom RANDOM = new SecureRandom();
    private Database() {}

    static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }
        return c;
    }

    static void initialize() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS trains (train_number INTEGER PRIMARY KEY, train_name TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS reservations (pnr_number TEXT PRIMARY KEY, passenger_name TEXT NOT NULL, train_number INTEGER NOT NULL REFERENCES trains(train_number), train_name TEXT NOT NULL, class_type TEXT NOT NULL, journey_date TEXT NOT NULL, source_station TEXT NOT NULL, destination_station TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    static void register(String username, char[] password) throws Exception {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        byte[] hash = hash(password, salt);
        String encoded = "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("INSERT INTO users(username,password_hash) VALUES(?,?)")) {
            p.setString(1, username.trim()); p.setString(2, encoded); p.executeUpdate();
        }
    }

    static boolean login(String username, char[] password) throws Exception {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT password_hash FROM users WHERE username=?")) {
            p.setString(1, username.trim());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return false;
                String[] parts = r.getString(1).split("\\$", -1);
                if (parts.length != 3 || !parts[0].equals("pbkdf2")) return false;
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                return MessageDigest.isEqual(expected, hash(password, salt));
            }
        }
    }

    private static byte[] hash(char[] password, byte[] salt) throws Exception {
        var spec = new javax.crypto.spec.PBEKeySpec(password, salt, 210_000, 256);
        try { return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    static String trainName(int number) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT train_name FROM trains WHERE train_number=?")) {
            p.setInt(1, number);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    static int importTrains(Path csv) throws Exception {
        int count = 0;
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
                 PreparedStatement p = c.prepareStatement("INSERT INTO trains(train_number,train_name) VALUES(?,?) ON CONFLICT(train_number) DO UPDATE SET train_name=excluded.train_name")) {
                String line; int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++; if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                    String[] fields = line.split(",", 2);
                    if (lineNo == 1 && fields[0].trim().equalsIgnoreCase("train_number")) continue;
                    if (fields.length != 2 || !fields[0].trim().matches("\\d+") || fields[1].trim().isEmpty())
                        throw new IllegalArgumentException("Invalid CSV row " + lineNo + ". Expected train_number,train_name.");
                    p.setInt(1, Integer.parseInt(fields[0].trim())); p.setString(2, fields[1].trim()); p.addBatch(); count++;
                }
                p.executeBatch(); c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }
        return count;
    }

    static String book(Booking b) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String name;
                try (PreparedStatement p = c.prepareStatement("SELECT train_name FROM trains WHERE train_number=?")) {
                    p.setInt(1, b.trainNumber());
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("Train number not found. Import the current train list first.");
                        name = r.getString(1);
                    }
                }
                for (int attempt = 0; attempt < 5; attempt++) {
                    String pnr = newPnr();
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO reservations(pnr_number,passenger_name,train_number,train_name,class_type,journey_date,source_station,destination_station) VALUES(?,?,?,?,?,?,?,?)")) {
                        p.setString(1, pnr); p.setString(2, b.passengerName().trim()); p.setInt(3, b.trainNumber());
                        p.setString(4, name); p.setString(5, b.classType()); p.setString(6, b.journeyDate().toString());
                        p.setString(7, b.source().trim()); p.setString(8, b.destination().trim()); p.executeUpdate();
                        c.commit(); return pnr;
                    } catch (SQLException e) {
                        if (!e.getMessage().contains("reservations.pnr_number") || attempt == 4) throw e;
                    }
                }
                throw new SQLException("Could not generate a unique PNR. Please try again.");
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }

    private static String newPnr() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder value = new StringBuilder("RE");
        for (int i = 0; i < 8; i++) value.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return value.toString();
    }

    static Booking find(String pnr) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT passenger_name,train_number,train_name,class_type,journey_date,source_station,destination_station FROM reservations WHERE pnr_number=?")) {
            p.setString(1, pnr.trim().toUpperCase());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                return new Booking(r.getString(1), r.getInt(2), r.getString(3), r.getString(4), LocalDate.parse(r.getString(5)), r.getString(6), r.getString(7), pnr.trim().toUpperCase());
            }
        }
    }

    static boolean cancel(String pnr) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("DELETE FROM reservations WHERE pnr_number=?")) {
            p.setString(1, pnr.trim().toUpperCase()); return p.executeUpdate() == 1;
        }
    }

    record Booking(String passengerName, int trainNumber, String trainName, String classType, LocalDate journeyDate,
                   String source, String destination, String pnr) {
        Booking(String passengerName, int trainNumber, String trainName, String classType, LocalDate journeyDate, String source, String destination) {
            this(passengerName, trainNumber, trainName, classType, journeyDate, source, destination, null);
        }
    }
}
