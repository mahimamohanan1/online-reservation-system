package com.railease;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
class ApiController {
    private final UserRepository users; private final TrainRepository trains; private final ReservationRepository reservations; private final PasswordEncoder passwords;
    private final SecureRandom random=new SecureRandom();
    ApiController(UserRepository users,TrainRepository trains,ReservationRepository reservations,PasswordEncoder passwords){this.users=users;this.trains=trains;this.reservations=reservations;this.passwords=passwords;}

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
    private void establishManualSession(AppUser user,HttpServletRequest request){
        if(request.getSession(false)!=null) request.changeSessionId();
        HttpSession session=request.getSession(true);session.setAttribute("userId",user.id);
        var authentication=new UsernamePasswordAuthenticationToken(user.username,null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    @GetMapping("/auth/me") Map<String,String> me(Authentication auth){
        if(auth.getPrincipal() instanceof OAuth2User google){
            String email=google.getAttribute("email");String name=google.getAttribute("name");
            return Map.of("username",email==null?auth.getName():email,"displayName",name==null?auth.getName():name);
        }
        return Map.of("username",auth.getName(),"displayName",auth.getName());
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
    record TrainView(Integer trainNumber,String trainName){}
    record ReservationInput(@NotBlank @Size(max=120) String passengerName,@NotNull @Positive Integer trainNumber,@NotBlank @Size(max=40) String classType,@NotNull LocalDate journeyDate,@NotBlank @Size(max=120) String sourceStation,@NotBlank @Size(max=120) String destinationStation){}
    record ReservationView(String pnrNumber,String passengerName,Integer trainNumber,String trainName,String classType,LocalDate journeyDate,String sourceStation,String destinationStation,String bookingStatus){}
}
