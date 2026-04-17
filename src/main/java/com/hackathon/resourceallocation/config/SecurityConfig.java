package com.hackathon.resourceallocation.config;

import com.hackathon.resourceallocation.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // enables @PreAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    //Password Encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    //Authentication Provider
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    //Security Filter Chain
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (stateless JWT API)
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — no HttpSession
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Custom error responses
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                //Route Authorization Rules
                .authorizeHttpRequests(auth -> auth

                        //Public endpoints (no token required)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/images/**").permitAll()

                        // Anyone can submit a community need (public kiosk/form)
                        .requestMatchers(HttpMethod.POST, "/api/needs").permitAll()

                        //VOLUNTEER role
                        // Volunteers can view their own tasks and update status
                        .requestMatchers(HttpMethod.GET, "/api/tasks/by-volunteer/**").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/*/status").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")

                        // Volunteers can view available needs and map
                        .requestMatchers(HttpMethod.GET, "/api/needs").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/needs/map").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/needs/*").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")

                        // Volunteers can view/update their own profile
                        .requestMatchers(HttpMethod.GET, "/api/volunteers/*").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/volunteers/*").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/volunteers/*/availability").hasAnyRole("VOLUNTEER", "COORDINATOR", "ADMIN")

                        //COORDINATOR role
                        // Coordinators manage everything except user admin
                        .requestMatchers(HttpMethod.GET, "/api/volunteers").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/volunteers").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/tasks").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks/by-need/**").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/needs/*/status").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/needs/*/matches").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/needs/*/analyze").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole("COORDINATOR", "ADMIN")

                        //ADMIN role only
                        // User management, deletions
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/needs/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/volunteers/*").hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Register auth provider and JWT filter
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}