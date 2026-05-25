package com.example.hrms.repository;

import com.example.hrms.entity.Site;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByActiveTrue();
}
