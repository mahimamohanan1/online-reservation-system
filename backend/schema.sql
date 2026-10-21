CREATE DATABASE IF NOT EXISTS railease CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE railease;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trains (
  train_number INT NOT NULL PRIMARY KEY,
  train_name VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
  pnr_number VARCHAR(16) NOT NULL PRIMARY KEY,
  passenger_name VARCHAR(120) NOT NULL,
  train_number INT NOT NULL,
  train_name VARCHAR(160) NOT NULL,
  class_type VARCHAR(40) NOT NULL,
  journey_date DATE NOT NULL,
  source_station VARCHAR(120) NOT NULL,
  destination_station VARCHAR(120) NOT NULL,
  booking_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reservation_train (train_number),
  CONSTRAINT fk_reservation_train FOREIGN KEY (train_number) REFERENCES trains(train_number)
);

-- Add operational train records to this table from your authoritative train dataset.
