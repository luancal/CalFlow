package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.Comissao;
import com.luancal.calflow.pagamento.domain.StatusComissao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ComissaoRepository extends JpaRepository<Comissao, String> {

    List<Comissao> findByStatus(StatusComissao status);

    List<Comissao> findByAfiliadoId(String afiliadoId);

    List<Comissao> findByGestorId(String gestorId);



    @Query("SELECT c FROM Comissao c WHERE c.afiliado.id = :afiliadoId AND c.status = :status")
    List<Comissao> findByAfiliadoIdAndStatus(String afiliadoId, StatusComissao status);

    @Query("SELECT c FROM Comissao c WHERE c.gestor.id = :gestorId AND c.status = :status")
    List<Comissao> findByGestorIdAndStatus(String gestorId, StatusComissao status);

    List<Comissao> findByVendaIdAndStatus(String vendaId, StatusComissao status);

    @Query("SELECT c FROM Comissao c WHERE c.status = :status " +
            "AND c.dataGeracao BETWEEN :inicio AND :fim")
    List<Comissao> findByStatusAndPeriodo(
            @Param("status") StatusComissao status,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim);
}
