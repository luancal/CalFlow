package com.luancal.calflow.pagamento.service;

import com.luancal.calflow.pagamento.domain.ClienteCalFlow;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    public String gerarToken(ClienteCalFlow cliente) {
        return Jwts.builder()
                .setSubject(cliente.getId())
                .claim("usuario", cliente.getUsuario())
                .claim("tipo", cliente.getTipo())
                .claim("clinicaId", cliente.getClinicaId())
                .claim("nome", cliente.getNome())
                .claim("negocio", cliente.getNomeNegocio())
                .claim("plano", cliente.getPlano())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000L * 7)) // 7 dias
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validarToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}