package com.webapp.jobportal.services;

import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.repository.JobPostActivityRepository;
import com.webapp.jobportal.repository.RecruiterProfileRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class JobPostActivityService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobPostActivityRepository jobPostActivityRepository;
    private final com.webapp.jobportal.repository.JobCompanyRepository jobCompanyRepository;
    private final com.webapp.jobportal.repository.JobModerationLogRepository jobModerationLogRepository;
    private final NotificationService notificationService;

    public JobPostActivityService(JobPostActivityRepository jobPostActivityRepository,
            RecruiterProfileRepository recruiterProfileRepository,
            com.webapp.jobportal.repository.JobCompanyRepository jobCompanyRepository,
            com.webapp.jobportal.repository.JobModerationLogRepository jobModerationLogRepository,
            NotificationService notificationService) {
        this.jobPostActivityRepository = jobPostActivityRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.jobCompanyRepository = jobCompanyRepository;
        this.jobModerationLogRepository = jobModerationLogRepository;
        this.notificationService = notificationService;
    }

    public JobPostActivity addNew(JobPostActivity jobPostActivity) {
        String oldStatus = jobPostActivity.getStatus();
        jobPostActivity.setIsApproved(true);
        jobPostActivity.setIsActive(true);

        // Auto-check for risky keywords
        if (checkKeywords(jobPostActivity)) {
            jobPostActivity.setStatus("UNDER_REVIEW");
            jobPostActivity.setIsActive(false); // Hide while under review
            jobPostActivity.setModerationNote("Automatically flagged for review due to suspicious keywords.");
        } else {
            jobPostActivity.setStatus("OPEN");
        }

        JobPostActivity saved = jobPostActivityRepository.save(jobPostActivity);
        logModerationAction(saved, oldStatus, saved.getStatus(), saved.getPostedById(),
                "Initial post submission - automated keyword check performed.", "SYSTEM_CHECK", "LOW");
        return saved;
    }

    public List<RecruiterJobsDto> getRecruiterJobs(int recruiter) {

        List<IRecruiterJobs> recruiterJobsDtos = jobPostActivityRepository.getRecruiterJobs(recruiter);

        List<RecruiterJobsDto> recruiterJobsDtoList = new ArrayList<>();

        for (IRecruiterJobs rec : recruiterJobsDtos) {
            JobLocation loc = new JobLocation(rec.getLocationId(), rec.getCity(), rec.getState(), rec.getCountry());
            JobCompany comp = new JobCompany(rec.getCompanyId(), rec.getName(), "");
            recruiterJobsDtoList.add(new RecruiterJobsDto(rec.getTotalCandidates(), rec.getJob_post_id(),
                    rec.getJob_title(), loc, comp, rec.getDescription_of_job(), rec.getPosted_date(), rec.getStatus(),
                    rec.getSeverity(), rec.getModerationNote()));
        }
        return recruiterJobsDtoList;

    }

    public JobPostActivity getOne(int id) {
        JobPostActivity job = jobPostActivityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        populateRecruiterDetails(job);
        return job;
    }

    public List<JobPostActivity> getAll() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<JobPostActivity> getAllIncludingPending() {
        return jobPostActivityRepository.findAll();
    }

    public List<JobPostActivity> search(String job, String location, List<String> type, List<String> remote,
            LocalDate searchDate, String category) {
        List<JobPostActivity> results = Objects.isNull(searchDate)
                ? jobPostActivityRepository.searchWithoutDate(job, location, remote, type, category)
                : jobPostActivityRepository.search(job, location, remote, type, category, searchDate);
        // Filter only active jobs (approval optional for visibility)
        return results.stream()
                .filter(j -> Boolean.TRUE.equals(j.getIsActive()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<JobPostActivity> searchWithSalary(String job, String location, List<String> type, List<String> remote,
            LocalDate searchDate, Double minSalary, Double maxSalary, String category) {
        List<JobPostActivity> results = search(job, location, type, remote, searchDate, category);
        if (minSalary != null || maxSalary != null) {
            results = results.stream()
                    .filter(j -> {
                        if (j.getSalaryMin() == null && j.getSalaryMax() == null)
                            return false;
                        if (minSalary != null && j.getSalaryMax() != null && j.getSalaryMax() < minSalary)
                            return false;
                        if (maxSalary != null && j.getSalaryMin() != null && j.getSalaryMin() > maxSalary)
                            return false;
                        return true;
                    })
                    .collect(java.util.stream.Collectors.toList());
        }
        return results;
    }

    public List<JobPostActivity> getPendingJobs() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> job.getIsApproved() == null || !job.getIsApproved())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<JobPostActivity> getApprovedJobs() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .collect(java.util.stream.Collectors.toList());
    }

    public JobPostActivity approveJob(int jobId) {
        JobPostActivity job = getOne(jobId);
        String oldStatus = job.getStatus();
        job.setIsApproved(true);
        job.setIsActive(true);
        job.setStatus("OPEN");
        JobPostActivity saved = jobPostActivityRepository.save(job);
        logModerationAction(saved, oldStatus, "OPEN", null, "Manual admin approval.", "NONE", "LOW");
        return saved;
    }

    public JobPostActivity rejectJob(int jobId) {
        JobPostActivity job = getOne(jobId);
        String oldStatus = job.getStatus();
        job.setIsApproved(false);
        job.setIsActive(false);
        job.setStatus("REJECTED");
        JobPostActivity saved = jobPostActivityRepository.save(job);
        logModerationAction(saved, oldStatus, "REJECTED", null, "Manual admin rejection.", "NONE", "LOW");
        return saved;
    }

    public JobPostActivity pauseJob(int jobId) {
        JobPostActivity job = getOne(jobId);
        String oldStatus = job.getStatus();
        job.setIsActive(false);
        job.setStatus("PAUSED");
        JobPostActivity saved = jobPostActivityRepository.save(job);
        logModerationAction(saved, oldStatus, "PAUSED", job.getPostedById(), "Recruiter paused the job.", "NONE",
                "LOW");
        return saved;
    }

    public JobPostActivity resumeJob(int jobId) {
        JobPostActivity job = getOne(jobId);
        String oldStatus = job.getStatus();
        job.setIsActive(true);
        job.setStatus("OPEN");
        JobPostActivity saved = jobPostActivityRepository.save(job);
        logModerationAction(saved, oldStatus, "OPEN", job.getPostedById(), "Recruiter resumed the job.", "NONE", "LOW");
        return saved;
    }

    public JobPostActivity markAsViolation(int jobId, String reason) {
        return markAsViolation(jobId, reason, "TOS_VIOLATION", "MEDIUM", null);
    }

    public JobPostActivity markAsViolation(int jobId, String reason, String category, String severity, Users actionBy) {
        JobPostActivity job = getOne(jobId);
        String oldStatus = job.getStatus();
        job.setIsApproved(false);
        job.setIsActive(false);
        job.setStatus("VIOLATION");
        job.setIsActive(false);
        job.setModerationNote(reason);
        job.setViolationCategory(category);
        job.setSeverity(severity);
        JobPostActivity saved = jobPostActivityRepository.save(job);
        logModerationAction(saved, oldStatus, "VIOLATION", actionBy, reason, category, severity);
        return saved;
    }

    private boolean checkKeywords(JobPostActivity job) {
        String content = (job.getJobTitle() + " " + job.getDescriptionOfJob()).toLowerCase();
        String[] riskyKeywords = { "telegram", "whatsapp", "money wire", "crypto", "investment", "whatsapp me",
                "contact us 0" };
        for (String key : riskyKeywords) {
            if (content.contains(key))
                return true;
        }
        return false;
    }

    private void logModerationAction(JobPostActivity job, String oldStatus, String newStatus, Users actionBy,
            String reason, String category, String severity) {
        JobModerationLog log = new JobModerationLog(job, oldStatus, newStatus, actionBy, reason, category, severity,
                new java.util.Date());
        jobModerationLogRepository.save(log);

        // Also create an in-dashboard notification for the recruiter
        if (job.getPostedById() != null) {
            String title = "Job Moderation: " + newStatus;
            String message = String.format("The status of your job '%s' changed from %s to %s.",
                    job.getJobTitle(), oldStatus, newStatus);
            if (reason != null && !reason.isEmpty()) {
                message += " Reason: " + reason;
            }
            notificationService.createNotification(job.getPostedById(), title, message, "JOB_MODERATION",
                    job.getJobPostId());
        }
    }

    public List<JobModerationLog> getJobModerationHistory(int jobId) {
        JobPostActivity job = getOne(jobId);
        return jobModerationLogRepository.findByJobOrderByTimestampDesc(job);
    }

    public List<JobModerationLog> getAllModerationLogs() {
        return jobModerationLogRepository.findAllByOrderByTimestampDesc();
    }

    public org.springframework.data.domain.Page<JobModerationLog> getModerationLogs(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return jobModerationLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public org.springframework.data.domain.Page<JobModerationLog> getModerationLogs(int page, int size, String status,
            String severity, String category, Integer moderatorId, java.util.Date startDate, java.util.Date endDate) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("timestamp").descending());
        return jobModerationLogRepository.findWithFilters(status, severity, category, moderatorId, startDate, endDate,
                pageable);
    }

    public long getTotalJobs() {
        return jobPostActivityRepository.count();
    }

    public long getPendingJobsCount() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> job.getIsApproved() == null || !job.getIsApproved())
                .count();
    }

    public List<JobPostActivity> getUnongoingJobs() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> Boolean.FALSE.equals(job.getIsActive()) ||
                        (job.getIsApproved() != null && !job.getIsApproved()))
                .collect(java.util.stream.Collectors.toList());
    }

    public long getUnongoingJobsCount() {
        return jobPostActivityRepository.findAll().stream()
                .filter(job -> Boolean.FALSE.equals(job.getIsActive()) ||
                        (job.getIsApproved() != null && !job.getIsApproved()))
                .count();
    }

    public void delete(int id) {
        JobPostActivity job = getOne(id);
        job.setIsActive(false);
        jobPostActivityRepository.save(job);
    }

    public List<JobPostActivity> getRecentJobs() {
        List<JobPostActivity> recentJobs = jobPostActivityRepository.getRecentJobs();
        if (recentJobs != null) {
            recentJobs.forEach(this::populateRecruiterDetails);
        }
        return recentJobs;
    }

    private void populateRecruiterDetails(JobPostActivity job) {
        if (job == null) return;
        if (job.getPostedById() != null) {
            try {
                recruiterProfileRepository.findById(job.getPostedById().getUserId()).ifPresent(profile -> {
                    job.setPostedByVerified(Boolean.TRUE.equals(profile.getIsVerified()));
                    job.setRecruiterFirstName(profile.getFirstName());
                    job.setRecruiterLastName(profile.getLastName());
                    job.setRecruiterCity(profile.getCity());
                    job.setRecruiterCountry(profile.getCountry());
                    job.setRecruiterProfilePhoto(profile.getPhotosImagePath());

                    String comp = profile.getCompany();
                    if (comp == null || comp.trim().isEmpty()) {
                        if (job.getJobCompanyId() != null && job.getJobCompanyId().getName() != null
                                && !job.getJobCompanyId().getName().trim().isEmpty()) {
                            comp = job.getJobCompanyId().getName();
                        } else {
                            String fullName = ((profile.getFirstName() != null ? profile.getFirstName().trim() : "") + " "
                                    + (profile.getLastName() != null ? profile.getLastName().trim() : "")).trim();
                            comp = !fullName.isEmpty() ? fullName : "Verified Client";
                        }
                    }
                    job.setRecruiterCompany(comp.trim());
                });
            } catch (Exception ignored) {
            }
        }
        if (job.getRecruiterCompany() == null || job.getRecruiterCompany().trim().isEmpty()) {
            if (job.getJobCompanyId() != null && job.getJobCompanyId().getName() != null
                    && !job.getJobCompanyId().getName().trim().isEmpty()) {
                job.setRecruiterCompany(job.getJobCompanyId().getName().trim());
            } else {
                job.setRecruiterCompany("Verified Client");
            }
        }
    }

    public List<JobCompany> getTrustedCompanies() {
        return jobCompanyRepository.findTop5();
    }
}
