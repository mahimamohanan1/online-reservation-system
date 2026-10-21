package com.railease;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="users")
class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,unique=true,length=80) String username;
    @Column(name="password_hash",nullable=false,length=100) String passwordHash;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt=Instant.now();
    protected AppUser() {}
}

@Entity @Table(name="trains")
class Train {
    @Id @Column(name="train_number") Integer trainNumber;
    @Column(name="train_name",nullable=false,length=160) String trainName;
    protected Train() {}
}

@Entity @Table(name="route_fares",uniqueConstraints=@UniqueConstraint(name="uk_route_fare",columnNames={"train_number","class_type","source_station","destination_station"}))
class RouteFare {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="train_number",nullable=false) Integer trainNumber;
    @Column(name="class_type",nullable=false,length=40) String classType;
    @Column(name="source_station",nullable=false,length=120) String sourceStation;
    @Column(name="destination_station",nullable=false,length=120) String destinationStation;
    @Column(name="amount_paise",nullable=false) Long amountPaise;
    protected RouteFare() {}
}

@Entity @Table(name="payment_attempts",uniqueConstraints=@UniqueConstraint(name="uk_payment_pnr",columnNames="pnr_number"))
class PaymentAttempt {
    @Id @Column(name="order_id",length=64) String orderId;
    @Column(name="pnr_number",nullable=false,length=16) String pnrNumber;
    @Column(name="passenger_name",nullable=false,length=120) String passengerName;
    @Column(name="train_number",nullable=false) Integer trainNumber;
    @Column(name="train_name",nullable=false,length=160) String trainName;
    @Column(name="class_type",nullable=false,length=40) String classType;
    @Column(name="journey_date",nullable=false) LocalDate journeyDate;
    @Column(name="source_station",nullable=false,length=120) String sourceStation;
    @Column(name="destination_station",nullable=false,length=120) String destinationStation;
    @Column(name="amount_paise",nullable=false) Long amountPaise;
    @Column(nullable=false,length=20) String status="CREATED";
    @Column(name="payment_id",unique=true,length=64) String paymentId;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt=Instant.now();
    protected PaymentAttempt() {}
}

@Entity @Table(name="reservations",indexes=@Index(name="idx_reservation_train",columnList="train_number"))
class Reservation {
    @Id @Column(name="pnr_number",length=16) String pnrNumber;
    @Column(name="passenger_name",nullable=false,length=120) String passengerName;
    @Column(name="train_number",nullable=false) Integer trainNumber;
    @Column(name="train_name",nullable=false,length=160) String trainName;
    @Column(name="class_type",nullable=false,length=40) String classType;
    @Column(name="journey_date",nullable=false) LocalDate journeyDate;
    @Column(name="source_station",nullable=false,length=120) String sourceStation;
    @Column(name="destination_station",nullable=false,length=120) String destinationStation;
    @Column(name="booking_status",nullable=false,length=20) String bookingStatus="CONFIRMED";
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt=Instant.now();
    protected Reservation() {}
}
