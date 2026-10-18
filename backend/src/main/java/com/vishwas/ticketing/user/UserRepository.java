package com.vishwas.ticketing.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select user from User user where lower(user.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
