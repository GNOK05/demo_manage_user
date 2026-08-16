package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.DatabaseUserDetailsService;
import java.util.List;


@Configuration
public class SecurityConfig  {
    private final JwtAuthenticationFilter jwtFilter;
    private final PasswordEncoder encoder;
    private final DatabaseUserDetailsService userDetailsService;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter, PasswordEncoder encoder, DatabaseUserDetailsService userDetailsService) { this.jwtFilter=jwtFilter; this.encoder=encoder; this.userDetailsService=userDetailsService; }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/api/v1/auth/**").permitAll()
                                .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean public DaoAuthenticationProvider authenticationProvider(){var p=new DaoAuthenticationProvider();p.setUserDetailsService(userDetailsService);p.setPasswordEncoder(encoder);return p;}
    @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration config)throws Exception{return config.getAuthenticationManager();}
    @Bean public CorsConfigurationSource corsConfigurationSource(){var c=new CorsConfiguration();c.setAllowedOrigins(List.of("http://localhost:4200"));c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type"));var s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/**",c);return s;}
}
