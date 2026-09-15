package com.webapp.jobportal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiter_profile")
public class RecruiterProfile {

    @Id
    @Column(name = "user_account_id")
    private int userAccountId;

    @OneToOne
    @JoinColumn(name = "user_account_id")
    @MapsId
    private Users userId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "company")
    private String company;

    @Column(name = "profile_photo", nullable = true, length = 500)
    private String profilePhoto;

    @Column(name = "verification_document", nullable = true, length = 500)
    private String verificationDocument;

    @Column(name = "business_license", nullable = true, length = 500)
    private String businessLicense;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(name = "document_status")
    private String documentStatus;

    @Column(name = "business_license_status")
    private String businessLicenseStatus;

    @Column(name = "verification_notes")
    private String verificationNotes;

    @Column(name = "tagline")
    private String tagline;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "website")
    private String website;

    public RecruiterProfile() {
    }

    public RecruiterProfile(int userAccountId, Users userId, String firstName, String lastName, String city,
            String state, String country, String company, String profilePhoto) {
        this.userAccountId = userAccountId;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.city = city;
        this.state = state;
        this.country = country;
        this.company = company;
        this.profilePhoto = profilePhoto;
    }

    public RecruiterProfile(Users users) {
        this.userId = users;
    }

    public int getUserAccountId() {
        return userAccountId;
    }

    public void setUserAccountId(int userAccountId) {
        this.userAccountId = userAccountId;
    }

    public Users getUserId() {
        return userId;
    }

    public void setUserId(Users userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    public String getVerificationDocument() {
        return verificationDocument;
    }

    public void setVerificationDocument(String verificationDocument) {
        this.verificationDocument = verificationDocument;
    }

    public String getBusinessLicense() {
        return businessLicense;
    }

    public void setBusinessLicense(String businessLicense) {
        this.businessLicense = businessLicense;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public String getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }

    public String getBusinessLicenseStatus() {
        return businessLicenseStatus;
    }

    public void setBusinessLicenseStatus(String businessLicenseStatus) {
        this.businessLicenseStatus = businessLicenseStatus;
    }

    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    @Transient
    public String getPhotosImagePath() {
        if (profilePhoto == null || profilePhoto.isEmpty())
            return null;
        if (profilePhoto.startsWith("http://") || profilePhoto.startsWith("https://")) {
            return profilePhoto;
        }
        return "/photos/recruiter/" + userAccountId + "/" + profilePhoto;
    }

    @Column(name = "verification_front", nullable = true, length = 500)
    private String verificationFront;

    @Column(name = "verification_back", nullable = true, length = 500)
    private String verificationBack;

    public String getVerificationFront() {
        return verificationFront;
    }

    public void setVerificationFront(String verificationFront) {
        this.verificationFront = verificationFront;
    }

    public String getVerificationBack() {
        return verificationBack;
    }

    public void setVerificationBack(String verificationBack) {
        this.verificationBack = verificationBack;
    }

    @Override
    public String toString() {
        return "RecruiterProfile{" +
                "userAccountId=" + userAccountId +
                ", userId=" + userId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", city='" + city + '\'' +
                ", state='" + state + '\'' +
                ", country='" + country + '\'' +
                ", company='" + company + '\'' +
                ", profilePhoto='" + profilePhoto + '\'' +
                '}';
    }
}