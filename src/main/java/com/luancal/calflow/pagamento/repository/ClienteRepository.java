package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, String> {

    Optional<Cliente> findByEmail(String email);

    Optional<Cliente> findByTelefone(String telefone);

    Optional<Cliente> findByCpfCnpj(String cpfCnpj);
}
