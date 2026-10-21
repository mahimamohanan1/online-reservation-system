package com.railease;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(request -> request.additionalParameters(parameters -> parameters.put("prompt", "select_account")));
        return resolver;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${app.allowed-origin}") String origin,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver) throws Exception {
        http.cors(cors -> {}).csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
              .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
          .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register").permitAll()
             .requestMatchers(HttpMethod.GET,"/api/trains/**").authenticated()
             .requestMatchers("/api/auth/**","/api/reservations/**","/api/payments/**").authenticated().anyRequest().permitAll())
          .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
          .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,exception) -> response.sendError(401)))
          .oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
              .successHandler((request,response,authentication) -> response.sendRedirect(origin + "/"))
              .failureHandler((request,response,exception) -> response.sendRedirect(origin + "/?authError=google")))
          .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable());
        return http.build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origin}") String origin) {
        CorsConfiguration config=new CorsConfiguration(); config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET","POST","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type","X-XSRF-TOKEN")); config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",config); return source;
    }
}
