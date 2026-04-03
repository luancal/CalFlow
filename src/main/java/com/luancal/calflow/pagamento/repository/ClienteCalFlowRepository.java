package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.ClienteCalFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteCalFlowRepository extends JpaRepository<ClienteCalFlow, String> {
    Optional<ClienteCalFlow> findByUsuario(String usuario);
    Optional<ClienteCalFlow> findByEmail(String email);
}