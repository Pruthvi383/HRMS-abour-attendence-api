package com.example.hrms.repository;

import com.example.hrms.entity.AttendanceLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @Query("SELECT a FROM AttendanceLog a JOIN FETCH a.site WHERE a.worker.id = :workerId AND a.clockOutTime IS NULL")
    Optional<AttendanceLog> findActiveByWorkerId(@Param("workerId") Long workerId);

    @Query(value = "SELECT a FROM AttendanceLog a JOIN FETCH a.worker JOIN FETCH a.site "
        + "WHERE a.worker.id = :workerId AND a.clockInTime >= :from AND a.clockInTime <= :to",
        countQuery = "SELECT COUNT(a) FROM AttendanceLog a WHERE a.worker.id = :workerId "
            + "AND a.clockInTime >= :from AND a.clockInTime <= :to")
    Page<AttendanceLog> findByWorkerAndDateRange(
        @Param("workerId") Long workerId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @Query("SELECT COALESCE(SUM(a.overtimeHours), 0) FROM AttendanceLog a "
        + "WHERE a.worker.id = :workerId "
        + "AND YEAR(a.clockInTime) = :year AND MONTH(a.clockInTime) = :month")
    BigDecimal sumOvertimeHoursForMonth(@Param("workerId") Long workerId,
                                        @Param("year") int year,
                                        @Param("month") int month);
}
