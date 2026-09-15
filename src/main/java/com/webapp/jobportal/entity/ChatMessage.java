package com.webapp.jobportal.entity;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "sender_id", referencedColumnName = "user_id")
    private Users senderId;

    @ManyToOne
    @JoinColumn(name = "receiver_id", referencedColumnName = "user_id")
    private Users receiverId;

    @ManyToOne
    @JoinColumn(name = "job_id", referencedColumnName = "job_post_id")
    private JobPostActivity jobId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @DateTimeFormat(pattern = "dd-MM-yyyy HH:mm:ss")
    @Column(name = "timestamp")
    private Date timestamp;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "attachment_path", length = 500)
    private String attachmentPath;

    @Column(name = "attachment_type")
    private String attachmentType;

    public ChatMessage() {
    }

    public ChatMessage(Integer id, Users senderId, Users receiverId, JobPostActivity jobId, String message,
            Date timestamp, Boolean isRead) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.jobId = jobId;
        this.message = message;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Users getSenderId() {
        return senderId;
    }

    public void setSenderId(Users senderId) {
        this.senderId = senderId;
    }

    public Users getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Users receiverId) {
        this.receiverId = receiverId;
    }

    public JobPostActivity getJobId() {
        return jobId;
    }

    public void setJobId(JobPostActivity jobId) {
        this.jobId = jobId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Boolean getIsEdited() {
        return isEdited;
    }

    public void setIsEdited(Boolean isEdited) {
        this.isEdited = isEdited;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }
}