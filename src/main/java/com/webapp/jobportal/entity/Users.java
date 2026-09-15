package com.webapp.jobportal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "users")
public class Users implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private int userId;

    @Column(unique = true)
    private String email;

    @NotEmpty
    private String password;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "is_approved")
    private boolean isApproved;

    @DateTimeFormat(pattern = "dd-MM-yyyy")
    @Column(name = "registration_date")
    private Date registrationDate;

    @ManyToOne
    @JoinColumn(name = "user_type_id", referencedColumnName = "user_type_id")
    private UsersType userTypeId;

    public Users() {
    }

    public Users(int userId, String email, String password, boolean isActive, boolean isApproved, Date registrationDate,
            UsersType userTypeId) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.isActive = isActive;
        this.isApproved = isApproved;
        this.registrationDate = registrationDate;
        this.userTypeId = userTypeId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean isApproved() {
        return isApproved;
    }

    public void setApproved(boolean approved) {
        isApproved = approved;
    }

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    @Column(nullable = true, length = 500)
    private String photos;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    public UsersType getUserTypeId() {
        return userTypeId;
    }

    public void setUserTypeId(UsersType userTypeId) {
        this.userTypeId = userTypeId;
    }

    public String getPhotos() {
        return photos;
    }

    public void setPhotos(String photos) {
        this.photos = photos;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    @Transient
    public String getPhotosImagePath() {
        if (photos == null || photos.isEmpty() || userId == 0)
            return null;
        if (photos.startsWith("http://") || photos.startsWith("https://")) {
            return photos;
        }
        return "/photos/users/" + userId + "/" + photos;
    }

    @Transient
    public String getFirstName() {
        return null; // Default for base Users entity
    }

    @Transient
    public String getLastName() {
        return null; // Default for base Users entity
    }

    @Override
    public String toString() {
        return "Users{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", isActive=" + isActive +
                ", registrationDate=" + registrationDate +
                ", userTypeId=" + userTypeId +
                '}';
    }
}