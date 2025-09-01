package com.hackerx.translation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackerx.translation.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}
