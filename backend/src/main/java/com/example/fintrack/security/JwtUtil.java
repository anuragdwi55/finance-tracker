package com.example.fintrack.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expirationMinutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMinutes * 60_000;
    }

    public String generateToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getSubject(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

  // validateToken boolean method having parameters token and email
    public Boolean validateToken(String token,String email){
        final String username= getSubject(token); // getting the subject of token
        return (username.equals(email)&& !isTokenExpired(token)); // checking if the email in token is same as given email and also checking whether it has expired or not.
    }

    // isTokenExpired boolean method having parameter token and taking expirytime from application.yml file.
    private boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }
    // extracting the expiration time from token
    private Date extractExpiration(String token){
        return Jwts.parserBuilder().setSigningKey(key).build()// building the parser with signingkey
                .parseClaimsJws(token)// parsing claims jws using token
                .getBody()// getting body of parsed claim jws
                .getExpiration();// returning expiration date of that body
    }
}
