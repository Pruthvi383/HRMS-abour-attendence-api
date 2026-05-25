package com.example.hrms.repository;

import com.example.hrms.entity.OvertimeEntry;
import com.example.hrms.enums.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    @Query("SELECT o FROM OvertimeEntry o JOIN FETCH o.attendanceLog "
        + "WHERE o.worker.id = :workerId AND YEAR(o.date) = :year AND MONTH(o.date) = :month")
    List<OvertimeEntry> findByWorkerAndMonth(@Param("workerId") Long workerId,
                                             @Param("year") int year,
                                             @Param("month") int month);

    @Query("SELECT COALESCE(SUM(o.overtimeHours), 0) FROM OvertimeEntry o "
        + "WHERE o.worker.id = :workerId AND YEAR(o.date) = :year AND MONTH(o.date) = :month")
    BigDecimal sumOvertimeHoursForMonth(@Param("workerId") Long workerId,
                                        @Param("year") int year,
                                        @Param("month") int month);

    boolean existsByWorkerIdAndDateBetweenAndSettlementStatus(
        Long workerId, LocalDate start, LocalDate end, SettlementStatus status);
}
