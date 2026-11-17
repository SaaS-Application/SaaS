package com.payroll.saas.repository;

import com.payroll.saas.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByTenantId(Long tenantId);
    List<Employee> findByCompanyIdAndTenantId(Long companyId, Long tenantId);
    Optional<Employee> findByUser_Id(Long userId);
    Optional<Employee> findByIdAndCompanyIdAndTenantId(Long id, Long companyId, Long tenantId);
    boolean existsByUser_Id(Long userId);
    boolean existsByCompanyIdAndEmployeeNumberIgnoreCase(Long companyId, String employeeNumber);
    boolean existsByCompanyIdAndEmailIgnoreCase(Long companyId, String email);
    boolean existsByCompanyIdAndEmployeeNumberIgnoreCaseAndIdNot(Long companyId, String employeeNumber, Long id);
    boolean existsByCompanyIdAndEmailIgnoreCaseAndIdNot(Long companyId, String email, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from Employee e
            where e.id = :employeeId and e.companyId = :companyId and e.tenantId = :tenantId
            """)
    Optional<Employee> findForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId);

    @Query("""
            select e from Employee e
            where e.companyId = :companyId and e.tenantId = :tenantId
              and (:search is null
                   or lower(e.firstName) like lower(concat('%', :search, '%'))
                   or lower(e.lastName) like lower(concat('%', :search, '%'))
                   or lower(concat(e.firstName, ' ', e.lastName)) like lower(concat('%', :search, '%'))
                   or lower(e.employeeNumber) like lower(concat('%', :search, '%')))
              and (:departmentId is null or e.department.id = :departmentId)
              and (:designationId is null or e.designation.id = :designationId)
              and (:employmentStatus is null or e.employmentStatus = :employmentStatus)
              and (:status is null or e.status = :status)
            """)
    @EntityGraph(attributePaths = {"department", "designation"})
    Page<Employee> searchCompanyEmployees(
            @Param("companyId") Long companyId,
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("departmentId") Long departmentId,
            @Param("designationId") Long designationId,
            @Param("employmentStatus") String employmentStatus,
            @Param("status") String status,
            Pageable pageable);
}
