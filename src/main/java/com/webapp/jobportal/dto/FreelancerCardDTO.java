package com.webapp.jobportal.dto;

import com.webapp.jobportal.entity.JobSeekerProfile;

public class FreelancerCardDTO {
    private JobSeekerProfile profile;
    private Double rating;
    private String ratingFormatted;
    private Long reviewCount;

    public FreelancerCardDTO(JobSeekerProfile profile, Double rating, Long reviewCount) {
        this.profile = profile;
        this.rating = rating;
        this.reviewCount = reviewCount;
        // Format rating to 1 decimal place
        this.ratingFormatted = String.format("%.1f", rating != null ? rating : 0.0);
    }

    public JobSeekerProfile getProfile() {
        return profile;
    }

    public void setProfile(JobSeekerProfile profile) {
        this.profile = profile;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getRatingFormatted() {
        return ratingFormatted;
    }

    public void setRatingFormatted(String ratingFormatted) {
        this.ratingFormatted = ratingFormatted;
    }

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public boolean isVerified() {
        return profile != null && Boolean.TRUE.equals(profile.getIsVerified());
    }

    public Boolean getIsVerified() {
        return isVerified();
    }
}
