package com.webapp.jobportal.repository;

import com.webapp.jobportal.entity.JobSeekerProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, Integer> {

    @Query("SELECT DISTINCT p.country FROM JobSeekerProfile p WHERE p.country IS NOT NULL")
    List<String> findDistinctCountries();

    @Modifying
    @Transactional
    @Query(value = "CALL VerifyJobSeeker(:userId, :adminId, :approve, :notes)", nativeQuery = true)
    void verifyJobSeeker(@Param("userId") int userId, @Param("adminId") int adminId,
            @Param("approve") boolean approve, @Param("notes") String notes);

    @Query("SELECT p FROM JobSeekerProfile p LEFT JOIN FETCH p.skills WHERE p.userAccountId = :id")
    java.util.Optional<JobSeekerProfile> findByIdWithSkills(@Param("id") int id);

    long countByIsVerifiedTrue();

    @Query(value = "SELECT p.* FROM job_seeker_profile p JOIN users u ON p.user_account_id = u.user_id WHERE u.is_active = true AND u.is_approved = true AND p.is_verified = true ORDER BY p.user_account_id DESC LIMIT 4", nativeQuery = true)
    java.util.List<JobSeekerProfile> getRecentProfiles();
}
