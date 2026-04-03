package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.Afiliado;
import com.luancal.calflow.pagamento.domain.StatusAfiliado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AfiliadoRepository extends JpaRepository<Afiliado, String> {

    Optional<Afiliado> findByCodigoReferencia(String codigoReferencia);

    Optional<Afiliado> findByEmail(String email);

    List<Afiliado> findByStatus(StatusAfiliado status);

    List<Afiliado> findByGestorId(String gestorId);
}
