package com.railease;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@RestController @RequestMapping("/api")
class ApiController {
    private final UserRepository users; private final TrainRepository trains; private final ReservationRepository reservations; private final PasswordEncoder passwords; private final FirebaseTokenVerifier firebaseTokens;
    private final SecureRandom random=new SecureRandom();
    ApiController(UserRepository users,TrainRepository trains,ReservationRepository reservations,PasswordEncoder passwords,FirebaseTokenVerifier firebaseTokens){this.users=users;this.trains=trains;this.reservations=reservations;this.passwords=passwords;this.firebaseTokens=firebaseTokens;}

    @GetMapping("/auth/csrf") Map<String,String> csrf(org.springframework.security.web.csrf.CsrfToken token){ token.getToken(); return Map.of("token",token.getToken()); }
    @PostMapping("/auth/register") @Transactional Map<String,String> register(@Valid @RequestBody Credentials input,HttpServletRequest request){
        String username=input.username().trim();
        if(users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"That username is already in use. Try signing in instead.");
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be 72 bytes or fewer.");
        AppUser user=new AppUser();user.username=username;user.passwordHash=passwords.encode(input.password());users.saveAndFlush(user);
        establishManualSession(user,request);return Map.of("username",user.username,"displayName",user.username);
    }
    @PostMapping("/auth/login") Map<String,String> login(@Valid @RequestBody Credentials input,HttpServletRequest request){
        AppUser user=users.findByUsername(input.username().trim()).orElse(null);
        if(user==null||!passwords.matches(input.password(),user.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password.");
        establishManualSession(user,request);return Map.of("username",user.username,"displayName",user.username);
    }
    @PostMapping("/auth/firebase") @Transactional Map<String,String> firebaseLogin(@Valid @RequestBody FirebaseCredentials input,HttpServletRequest request){
        FirebaseToken token;
        try { token=firebaseTokens.verify(input.idToken()); }
        catch(FirebaseAuthException e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Google sign-in could not be verified. Please try again.");}
        Map<String,Object> claims=token.getClaims();
        if(!Boolean.TRUE.equals(claims.get("email_verified"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Use a Google account with a verified email address.");
        String email=token.getEmail();
        if(email==null||email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Google did not provide an email address for this account.");
        email=email.trim().toLowerCase(Locale.ROOT);
        if(email.length()>80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This Google email address is too long for a RailEase username.");
        AppUser user=users.findByUsername(email).orElse(null);
        if(user==null){user=new AppUser();user.username=email;user.passwordHash=passwords.encode(UUID.randomUUID().toString());user=users.saveAndFlush(user);}
        establishManualSession(user,request);
        String displayName=claims.get("name") instanceof String name&&!name.isBlank()?name:email;
        request.getSession(false).setAttribute("displayName",displayName);
        return Map.of("username",email,"displayName",displayName);
    }
    private void establishManualSession(AppUser user,HttpServletRequest request){
        if(request.getSession(false)!=null) request.changeSessionId();
        HttpSession session=request.getSession(true);session.setAttribute("userId",user.id);session.setAttribute("displayName",user.username);
        var authentication=new UsernamePasswordAuthenticationToken(user.username,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    @GetMapping("/auth/me") Map<String,String> me(Authentication auth,HttpServletRequest request){
        Object displayName=request.getSession(false).getAttribute("displayName");
        return Map.of("username",auth.getName(),"displayName",displayName instanceof String name?name:auth.getName());
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest request){HttpSession session=request.getSession(false);if(session!=null)session.invalidate();}
    @GetMapping("/trains/{number}") TrainView train(@PathVariable Integer number){Train t=trains.findById(number).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Train number not found"));return new TrainView(t.trainNumber,t.trainName);}
    @PostMapping("/reservations") @Transactional ReservationView book(@Valid @RequestBody ReservationInput input){
        if(input.journeyDate().isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Journey date cannot be in the past");
        if(input.sourceStation().trim().equalsIgnoreCase(input.destinationStation().trim())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Source and destination need to be different");
        Train train=trains.findById(input.trainNumber()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Train number not found"));
        Reservation r=new Reservation();r.pnrNumber=newPnr();r.passengerName=input.passengerName().trim();r.trainNumber=train.trainNumber;r.trainName=train.trainName;r.classType=input.classType();r.journeyDate=input.journeyDate();r.sourceStation=input.sourceStation().trim();r.destinationStation=input.destinationStation().trim();r.bookingStatus="CONFIRMED";
        return view(reservations.saveAndFlush(r));
    }
    @GetMapping("/reservations/{pnr}") ReservationView find(@PathVariable String pnr){Reservation r=reservations.findById(pnr.trim().toUpperCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No active booking was found for that PNR"));return view(r);}
    @DeleteMapping("/reservations/{pnr}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional void cancel(@PathVariable String pnr){Reservation r=reservations.findById(pnr.trim().toUpperCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"No active booking was found for that PNR"));reservations.delete(r);}
    private String newPnr(){for(int i=0;i<8;i++){String p="RE"+String.format("%010d",random.nextLong(1_000_000_000L));if(!reservations.existsById(p))return p;}throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Could not generate a booking reference. Please retry.");}
    private ReservationView view(Reservation r){return new ReservationView(r.pnrNumber,r.passengerName,r.trainNumber,r.trainName,r.classType,r.journeyDate,r.sourceStation,r.destinationStation,r.bookingStatus);}
    record Credentials(@NotBlank @Size(min=3,max=80) String username,@NotBlank @Size(min=12,max=72) String password){}
    record FirebaseCredentials(@NotBlank @Size(max=8192) String idToken){}
    record TrainView(Integer trainNumber,String trainName){}
    record ReservationInput(@NotBlank @Size(max=120) String passengerName,@NotNull @Positive Integer trainNumber,@NotBlank @Size(max=40) String classType,@NotNull LocalDate journeyDate,@NotBlank @Size(max=120) String sourceStation,@NotBlank @Size(max=120) String destinationStation){}
    record ReservationView(String pnrNumber,String passengerName,Integer trainNumber,String trainName,String classType,LocalDate journeyDate,String sourceStation,String destinationStation,String bookingStatus){}
}
