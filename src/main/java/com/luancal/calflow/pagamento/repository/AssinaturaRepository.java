package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.Assinatura;
import com.luancal.calflow.pagamento.domain.StatusAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssinaturaRepository extends JpaRepository<Assinatura, String> {
    Optional<Assinatura> findByAssinaturaGatewayId(String assinaturaGatewayId);

    List<Assinatura> findByStatus(StatusAssinatura status);

    List<Assinatura> findByClienteId(String clienteId);
    Optional<Assinatura> findByClienteIdAndStatus(String clienteId, StatusAssinatura status);

    @Query("SELECT a FROM Assinatura a WHERE a.status = :status AND a.dataProximaCobranca = :data")
    List<Assinatura> findByStatusAndDataProximaCobranca(StatusAssinatura status, LocalDate data);
}
