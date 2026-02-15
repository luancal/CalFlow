package com.luancal.calflow.repository;

import com.luancal.calflow.model.TipoServico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TipoServicoRepository extends JpaRepository<TipoServico, Long> {
    List<TipoServico> findByClinicaId(Long clinicaId);
}