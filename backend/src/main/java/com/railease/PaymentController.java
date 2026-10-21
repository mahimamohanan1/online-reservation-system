package com.railease;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;

@RestController @RequestMapping("/api/payments")
class PaymentController {
    private final PaymentService payments;
    PaymentController(PaymentService payments){this.payments=payments;}

    @PostMapping("/order") Map<String,Object> create(@Valid @RequestBody ApiController.ReservationInput input){return payments.createOrder(input);}
    @PostMapping("/verify") ApiController.ReservationView verify(@Valid @RequestBody VerifyInput input){return payments.verify(input);}
    record VerifyInput(@jakarta.validation.constraints.NotBlank String orderId,
                       @jakarta.validation.constraints.NotBlank String paymentId,
                       @jakarta.validation.constraints.NotBlank String signature){}
}

@Service
class PaymentService {
    private final TrainRepository trains; private final RouteFareRepository fares; private final PaymentAttemptRepository attempts;
    private final ReservationRepository reservations; private final String keyId; private final String keySecret; private final SecureRandom random=new SecureRandom();
    PaymentService(TrainRepository trains,RouteFareRepository fares,PaymentAttemptRepository attempts,ReservationRepository reservations,
            @Value("${app.razorpay.key-id:}") String keyId,@Value("${app.razorpay.key-secret:}") String keySecret){
        this.trains=trains;this.fares=fares;this.attempts=attempts;this.reservations=reservations;this.keyId=keyId;this.keySecret=keySecret;
    }

    @Transactional Map<String,Object> createOrder(ApiController.ReservationInput input){
        ensureConfigured();
        validate(input);
        Train train=trains.findById(input.trainNumber()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Train number not found"));
        String source=input.sourceStation().trim(),destination=input.destinationStation().trim(),classType=input.classType().trim();
        RouteFare fare=fares.findByTrainNumberAndClassTypeIgnoreCaseAndSourceStationIgnoreCaseAndDestinationStationIgnoreCase(train.trainNumber,classType,source,destination)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No fare is configured for this train, class and route. Please contact the railway administrator."));
        if(fare.amountPaise==null||fare.amountPaise<100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The configured fare is invalid.");
        String pnr=newPnr();
        Map<String,Object> result=razorpay().post().uri("/v1/orders").body(Map.of("amount",fare.amountPaise,"currency","INR","receipt",pnr))
                .retrieve().body(new ParameterizedTypeReference<>(){});
        if(result==null||!(result.get("id") instanceof String orderId))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"The payment provider did not return an order.");
        PaymentAttempt attempt=new PaymentAttempt();attempt.orderId=orderId;attempt.pnrNumber=pnr;attempt.passengerName=input.passengerName().trim();attempt.trainNumber=train.trainNumber;attempt.trainName=train.trainName;
        attempt.classType=classType;attempt.journeyDate=input.journeyDate();attempt.sourceStation=source;attempt.destinationStation=destination;attempt.amountPaise=fare.amountPaise;attempt.status="CREATED";
        attempts.saveAndFlush(attempt);
        return Map.of("keyId",keyId,"orderId",orderId,"amountPaise",fare.amountPaise,"currency","INR","pnrNumber",pnr);
    }

    @Transactional ApiController.ReservationView verify(PaymentController.VerifyInput input){
        ensureConfigured();
        PaymentAttempt attempt=attempts.findById(input.orderId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment order was not found."));
        if("PAID".equals(attempt.status))return reservations.findById(attempt.pnrNumber).map(this::view).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Paid reservation could not be loaded."));
        if(!"CREATED".equals(attempt.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"This payment order is no longer active.");
        if(!validSignature(input.orderId(),input.paymentId(),input.signature()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment verification failed. No ticket was booked.");
        Map<String,Object> payment=razorpay().get().uri("/v1/payments/{id}",input.paymentId()).retrieve().body(new ParameterizedTypeReference<>(){});
        if(payment==null||!input.orderId().equals(payment.get("order_id"))||!(payment.get("amount") instanceof Number amount)||amount.longValue()!=attempt.amountPaise||!Boolean.TRUE.equals(payment.get("captured")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment is not captured for this order. No ticket was booked.");
        Reservation reservation=new Reservation();reservation.pnrNumber=attempt.pnrNumber;reservation.passengerName=attempt.passengerName;reservation.trainNumber=attempt.trainNumber;reservation.trainName=attempt.trainName;
        reservation.classType=attempt.classType;reservation.journeyDate=attempt.journeyDate;reservation.sourceStation=attempt.sourceStation;reservation.destinationStation=attempt.destinationStation;reservation.bookingStatus="CONFIRMED";
        reservation=reservations.saveAndFlush(reservation);attempt.paymentId=input.paymentId();attempt.status="PAID";attempts.save(attempt);return view(reservation);
    }

    private void validate(ApiController.ReservationInput input){
        if(input.journeyDate().isBefore(LocalDate.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Journey date cannot be in the past.");
        if(input.sourceStation().trim().equalsIgnoreCase(input.destinationStation().trim()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination need to be different.");
    }
    private String newPnr(){for(int i=0;i<10;i++){String p="RE"+String.format("%010d",random.nextLong(1_000_000_000L));if(!reservations.existsById(p)&&!attempts.existsByPnrNumber(p))return p;}throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Could not generate a booking reference. Please retry.");}
    private boolean validSignature(String orderId,String paymentId,String signature){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String expected=HexFormat.of().formatHex(mac.doFinal((orderId+"|"+paymentId).getBytes(StandardCharsets.UTF_8)));return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII));}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not verify the payment signature.");}}
    private RestClient razorpay(){ensureConfigured();return RestClient.builder().baseUrl("https://api.razorpay.com").defaultHeaders(headers->headers.setBasicAuth(keyId,keySecret)).build();}
    private void ensureConfigured(){if(keyId.isBlank()||keySecret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Online payment is not configured. Add the Razorpay test keys to the backend environment.");}
    private ApiController.ReservationView view(Reservation r){return new ApiController.ReservationView(r.pnrNumber,r.passengerName,r.trainNumber,r.trainName,r.classType,r.journeyDate,r.sourceStation,r.destinationStation,r.bookingStatus);}
}
