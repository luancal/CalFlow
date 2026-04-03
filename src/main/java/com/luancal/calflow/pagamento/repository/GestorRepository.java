package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.Gestor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GestorRepository extends JpaRepository<Gestor, String> {

    Optional<Gestor> findByCodigoConvite(String codigoConvite);

    Optional<Gestor> findByEmail(String email);
}
