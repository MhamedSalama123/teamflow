package com.teamflow.backend.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByResetToken(String resetToken);

    boolean existsByEmail(String email);

    /**
     * Directory search over active (non-deleted) users. Each argument is optional: when null it is
     * ignored, otherwise it is matched case-insensitively as a substring. {@code q} matches either the
     * username or the full name.
     */
    // Each parameter is CAST to string so a null bind keeps a text type: without it PostgreSQL infers
    // bytea for the null and fails to resolve lower()/concat() while planning the query.
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (CAST(:q AS string) IS NULL
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (CAST(:jobTitle AS string) IS NULL
                   OR LOWER(u.jobTitle) LIKE LOWER(CONCAT('%', CAST(:jobTitle AS string), '%')))
              AND (CAST(:location AS string) IS NULL
                   OR LOWER(u.location) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%')))
            """)
    Page<User> search(
            @Param("q") String q,
            @Param("jobTitle") String jobTitle,
            @Param("location") String location,
            Pageable pageable);
}
