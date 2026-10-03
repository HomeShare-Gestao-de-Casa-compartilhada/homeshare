package com.homeshare.casa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CasaRepository extends JpaRepository<Casa, Long> {

    Optional<Casa> findByCodigoConvite(String codigoConvite);

    boolean existsByCodigoConvite(String codigoConvite);
}
