package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.StatusVenda;
import com.luancal.calflow.pagamento.domain.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, String> {

    Optional<Venda> findByGatewayTransacaoId(String gatewayTransacaoId);

    List<Venda> findByStatus(StatusVenda status);

    List<Venda> findByClienteId(String clienteId);

    List<Venda> findByAfiliadoId(String afiliadoId);

    @Query("SELECT v FROM Venda v WHERE v.status = :status AND v.dataVenda BETWEEN :inicio AND :fim")
    List<Venda> findByStatusAndPeriodo(StatusVenda status, LocalDateTime inicio, LocalDateTime fim);
}
