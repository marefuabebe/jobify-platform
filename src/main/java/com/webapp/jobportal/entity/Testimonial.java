package com.webapp.jobportal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "testimonials")
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String role;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer rating;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    public Testimonial() {
    }

    public Testimonial(String name, String role, String message, Integer rating, String imageUrl) {
        this.name = name;
        this.role = role;
        this.message = message;
        this.rating = rating;
        this.imageUrl = imageUrl;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Transient
    public String getPhotosImagePath() {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }
        if (id == null) {
            return null;
        }
        return "/photos/testimonials/" + id + "/" + imageUrl;
    }
}
