package com.webapp.jobportal.repository;

import com.webapp.jobportal.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Integer> {
    Optional<Users> findByEmail(String email);

    @Query("SELECT u FROM Users u WHERE u.userTypeId.userTypeName = :userType AND u.isApproved = true AND u.isActive = true AND u.userId != :currentUserId")
    List<Users> findVerifiedUsersByType(@Param("userType") String userType,
            @Param("currentUserId") Integer currentUserId);

    @Query("SELECT u FROM Users u WHERE u.userTypeId.userTypeId = :typeId AND u.userId != :currentUserId")
    List<Users> findVerifiedUsersByTypeId(@Param("typeId") int typeId, @Param("currentUserId") Integer currentUserId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET is_approved = :approve WHERE user_id = :userId", nativeQuery = true)
    int updateUserApprovalStatus(@Param("userId") int userId, @Param("approve") boolean approve);

    @Query("SELECT u FROM Users u WHERE u.userTypeId.userTypeId = 3")
    List<Users> findAllAdmins();
}
