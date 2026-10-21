package com.railease;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<AppUser, Long> {
    java.util.Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
interface TrainRepository extends JpaRepository<Train, Integer> { }
interface RouteFareRepository extends JpaRepository<RouteFare, Long> {
    java.util.Optional<RouteFare> findByTrainNumberAndClassTypeIgnoreCaseAndSourceStationIgnoreCaseAndDestinationStationIgnoreCase(Integer trainNumber,String classType,String sourceStation,String destinationStation);
}
interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> { boolean existsByPnrNumber(String pnrNumber); }
interface ReservationRepository extends JpaRepository<Reservation, String> { }
