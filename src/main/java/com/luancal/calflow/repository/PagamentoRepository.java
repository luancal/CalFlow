package com.luancal.calflow.repository;

import com.luancal.calflow.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Optional<Pagamento> findByMercadoPagoId(String mercadoPagoId);
    Optional<Pagamento> findByTelefoneClienteAndDataAgendamento(String telefone, String data);
    Optional<Pagamento> findByTelefoneClienteAndDataAgendamentoAndStatus(
            String telefone,
            String dataAgendamento,
            String status
    );
}
