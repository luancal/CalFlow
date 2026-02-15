package com.luancal.calflow.repository;

import com.luancal.calflow.model.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicaRepository extends JpaRepository<Clinica, Long> {
    Optional<Clinica> findBytelefoneDono(String telefoneDono);
    Optional<Clinica> findByNomeInstancia(String nomeInstancia);
}
