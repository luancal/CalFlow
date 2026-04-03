package com.luancal.calflow.pagamento.repository;

import com.luancal.calflow.pagamento.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, String> {

    Optional<Produto> findByNomeAndAtivoTrue(String nome);

    Optional<Produto> findFirstByAtivoTrue();
}

