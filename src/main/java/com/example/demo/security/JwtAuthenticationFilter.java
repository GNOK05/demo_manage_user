package com.example.demo.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final UserDetailsService detailsService;
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {String header=request.getHeader("Authorization");if(header==null||!header.startsWith("Bearer ")){chain.doFilter(request,response);return;}try{String token=header.substring(7);String username=jwt.username(token);if(SecurityContextHolder.getContext().getAuthentication()==null){UserDetails d=detailsService.loadUserByUsername(username);if(jwt.valid(token,d)){UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(d,null,d.getAuthorities());auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));SecurityContextHolder.getContext().setAuthentication(auth);}}}catch(Exception ignored){}chain.doFilter(request,response);}
}
