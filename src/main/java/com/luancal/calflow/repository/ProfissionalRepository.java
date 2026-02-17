package com.luancal.calflow.repository;

import com.luancal.calflow.model.Profissional;
import com.luancal.calflow.model.TipoServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {
    List<Profissional> findByClinicaIdAndAtivoTrue(Long clinicaId);
    List<Profissional> findByClinicaId(Long clinicaId);
    List<TipoServico> findByProfissionalId(Long profissionalId);
}
