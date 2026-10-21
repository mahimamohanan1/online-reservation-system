package com.railease;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<AppUser, Long> {
    java.util.Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
interface TrainRepository extends JpaRepository<Train, Integer> { }
interface ReservationRepository extends JpaRepository<Reservation, String> { }
