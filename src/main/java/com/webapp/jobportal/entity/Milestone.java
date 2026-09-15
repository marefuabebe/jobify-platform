package com.webapp.jobportal.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    private String description;
    private Double amount;

    private String status; // PENDING, FUNDED, IN_PROGRESS, SUBMITTED, APPROVED, AVAILABLE_BALANCE, WITHDRAWN, DISPUTED

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "created_date")
    private Date createdDate;

    @Column(name = "due_date")
    private Date dueDate;

    public Milestone() {
        this.createdDate = new Date();
    }

    public Milestone(Contract contract, String description, Double amount, String status) {
        this.contract = contract;
        this.description = description;
        this.amount = amount;
        this.status = status;
        this.createdDate = new Date();
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }
}
