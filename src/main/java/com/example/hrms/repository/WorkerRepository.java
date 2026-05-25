package com.example.hrms.repository;

import com.example.hrms.entity.Worker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    Optional<Worker> findByPhone(String phone);

    List<Worker> findByActiveTrue();
}
