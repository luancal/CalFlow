package com.luancal.calflow.repository;

import com.luancal.calflow.model.ServicoProfissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ServicoProfissionalRepository extends JpaRepository<ServicoProfissional, Long> {
    // Buscar profissionais que fazem determinado serviço
    @Query("SELECT sp FROM ServicoProfissional sp WHERE sp.tipoServico.id = :servicoId AND sp.ativo = true AND sp.profissional.ativo = true")
    List<ServicoProfissional> findProfissionaisByServico(Long servicoId);
    // Buscar serviços de um profissional
    List<ServicoProfissional> findByProfissionalIdAndAtivoTrue(Long profissionalId);
}
