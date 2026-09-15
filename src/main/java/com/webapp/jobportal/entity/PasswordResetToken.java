package com.webapp.jobportal.entity;

import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(targetEntity = Users.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id", referencedColumnName = "user_id")
    private Users user;

    @Column(nullable = false)
    private Date expiryDate;

    public PasswordResetToken() {
    }

    public PasswordResetToken(Users user) {
        this.user = user;
        this.token = UUID.randomUUID().toString();
        // 24 hours expiration
        this.expiryDate = new Date(System.currentTimeMillis() + (1000 * 60 * 60 * 24));
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }
}
