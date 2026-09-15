package com.webapp.jobportal.services;

import com.webapp.jobportal.entity.JobPostActivity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

        @Value("${jobify.email.proxy.url:}")
        private String proxyUrl;

        private final RestTemplate restTemplate = new RestTemplate();

        public void sendEmail(String to, String subject, String text) {
                sendEmail(to, subject, text, false);
        }

        public void sendEmail(String to, String subject, String text, boolean isHtml) {
                if (proxyUrl == null || proxyUrl.isEmpty() || proxyUrl.equals("YOUR_GAS_WEBAPP_URL")) {
                        System.err.println("Email proxy URL is not configured. Email to " + to + " was not sent.");
                        return;
                }

                try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        Map<String, String> payload = new HashMap<>();
                        payload.put("to", to);
                        payload.put("subject", subject);
                        payload.put("html", text); // GAS script expects 'html' key for HTML content, or we can just send it as 'html' and the GAS script sets htmlBody

                        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
                        
                        String response = restTemplate.postForObject(proxyUrl, request, String.class);
                        System.out.println("Email sent via Proxy. Response: " + response);
                } catch (Exception e) {
                        System.err.println("Error sending email via Proxy: " + e.getMessage());
                }
        }

        private String buildHtmlEmail(String title, String recipientName, String content, String callToAction,
                        String actionUrl) {
                String year = String.valueOf(java.time.Year.now().getValue());

                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>");
                html.append("<html lang='en'>");
                html.append("<head>");
                html.append("<meta charset='UTF-8'>");
                html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
                html.append("<title>").append(title).append("</title>");
                html.append("<style>");
                // Reset and Base Styles
                html.append(
                                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7fc; margin: 0; padding: 0; -webkit-font-smoothing: antialiased; }");
                html.append("table { width: 100%; border-collapse: collapse; }");

                // Container
                html.append(
                                ".email-container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.12); overflow: hidden; }");

                // Header
                html.append(
                                ".header { background: linear-gradient(135deg, #4B6CB7 0%, #182848 100%); padding: 40px 0; text-align: center; }");
                html.append(
                                ".header h1 { margin: 0; color: #ffffff; font-size: 28px; font-weight: 700; letter-spacing: 1px; display: inline-flex; align-items: center; gap: 10px; }");

                // Content
                html.append(".content { padding: 40px 40px 30px; color: #333333; line-height: 1.7; font-size: 16px; }");
                html.append(".greeting { font-size: 20px; font-weight: 600; margin-bottom: 24px; color: #1a1d1f; }");
                html.append(".message { margin-bottom: 30px; color: #5e6d55; }");

                // Info Box
                html.append(
                                ".info-box { background-color: #f8f9fa; border-left: 5px solid #4B6CB7; padding: 20px; margin: 25px 0; border-radius: 6px; font-size: 15px; color: #2c3e50; }");
                html.append(".info-box strong { color: #1a1d1f; }");

                // Button
                html.append(".btn-container { text-align: center; margin: 35px 0 10px; }");
                html.append(
                                ".btn { display: inline-block; padding: 14px 32px; background-color: #4B6CB7; color: #ffffff !important; text-decoration: none; border-radius: 50px; font-weight: 600; font-size: 16px; transition: all 0.3s ease; box-shadow: 0 4px 12px rgba(75, 108, 183, 0.3); }");
                html.append(".btn:hover { background-color: #324c8c; transform: translateY(-2px); box-shadow: 0 6px 16px rgba(75, 108, 183, 0.4); }");

                // Footer
                html.append(
                                ".footer { background-color: #f1f3f5; padding: 30px 40px; text-align: center; font-size: 13px; color: #868e96; border-top: 1px solid #e9ecef; }");
                html.append(".footer p { margin: 5px 0; }");
                html.append(".footer a { color: #4B6CB7; text-decoration: none; font-weight: 500; }");
                html.append(".social-links { margin: 15px 0; }");
                html.append(".social-links a { margin: 0 8px; font-size: 18px; color: #adb5bd; transition: color 0.2s; }");
                html.append(".social-links a:hover { color: #4B6CB7; }");

                // Responsive
                html.append("@media only screen and (max-width: 600px) {");
                html.append("  .email-container { width: 100% !important; margin: 0 !important; border-radius: 0 !important; }");
                html.append("  .content { padding: 30px 20px !important; }");
                html.append("  .header { padding: 30px 0 !important; }");
                html.append("}");

                html.append("</style>");
                html.append("</head>");
                html.append("<body>");

                html.append("<div class='email-container'>");

                // Header Section
                html.append("<div class='header'>");
                html.append("<h1><span style='font-size: 32px;'>💼</span> Jobify</h1>");
                html.append("</div>");

                // Content Section
                html.append("<div class='content'>");
                html.append("<div class='greeting'>Hello ").append(recipientName).append(",</div>");
                html.append("<div class='message'>").append(content).append("</div>");

                if (callToAction != null && actionUrl != null) {
                        html.append("<div class='btn-container'>");
                        html.append("<a href='").append(actionUrl).append("' class='btn'>").append(callToAction)
                                        .append("</a>");
                        html.append("</div>");
                }

                html.append("</div>"); // End Content

                // Footer Section
                html.append("<div class='footer'>");
                html.append("<p>&copy; ").append(year).append(" Jobify. All rights reserved.</p>");
                html.append("<p>Transforming how businesses and freelancers connect.</p>");
                html.append("<div style='margin-top: 20px; border-top: 1px solid #e0e0e0; padding-top: 15px;'>");
                html.append("<p>You are receiving this email because you have an account on Jobify.</p>");
                html.append("<p><a href='#'>Unsubscribe</a> | <a href='" + actionUrl
                                + "/info/privacy'>Privacy Policy</a> | <a href='#'>Support</a></p>");
                html.append("</div>");
                html.append("</div>"); // End Footer

                html.append("</div>"); // End Container
                html.append("</body>");
                html.append("</html>");

                return html.toString();
        }

        public void sendApplicationNotification(String freelancerEmail, String jobTitle, String clientName) {
                String subject = "Application Received - " + jobTitle;
                String content = String.format(
                                "<p>Your application for the job <strong>'%s'</strong> has been successfully submitted to <strong>%s</strong>.</p>"
                                                +
                                                "<p>The client will review your application and get back to you soon. Good luck!</p>",
                                jobTitle, clientName);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View My Applications",
                                "http://localhost:8080/dashboard/");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendApplicationStatusUpdate(String freelancerEmail, String jobTitle, String status) {
                String subject = "Application Status Update - " + jobTitle;
                String statusColor = "HIRED".equalsIgnoreCase(status) ? "#28a745"
                                : ("REJECTED".equalsIgnoreCase(status) ? "#dc3545" : "#007bff");

                String content = String.format(
                                "<p>Your application status for the job <strong>'%s'</strong> has been updated.</p>" +
                                                "<div class='info-box' style='border-left-color: %s;'><strong>New Status:</strong> <span style='color: %s; font-weight: bold;'>%s</span></div>",
                                jobTitle, statusColor, statusColor, status);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Application",
                                "http://localhost:8080/dashboard/");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendNewApplicationNotification(String clientEmail, String freelancerName, String jobTitle) {
                String subject = "New Application Received - " + jobTitle;
                String content = String.format(
                                "<p>Great news! You have received a new application from <strong>%s</strong> for the job <strong>'%s'</strong>.</p>"
                                                +
                                                "<p>Review their profile and proposal to see if they are a good fit for your project.</p>",
                                freelancerName, jobTitle);

                String htmlContent = buildHtmlEmail(subject, "Client", content, "Review Application",
                                "http://localhost:8080/dashboard/");
                sendEmail(clientEmail, subject, htmlContent, true);
        }

        public void sendJobApprovalNotification(String clientEmail, String jobTitle, boolean approved) {
                String subject = approved ? "Job Approved - " + jobTitle : "Job Requires Review - " + jobTitle;
                String content;

                if (approved) {
                        content = String.format(
                                        "<p>Congratulations! Your job posting <strong>'%s'</strong> has been <span style='color: #28a745; font-weight: bold;'>APPROVED</span> and is now live.</p>"
                                                        +
                                                        "<p>Your job is now visible to thousands of freelancers on our platform.</p>",
                                        jobTitle);
                } else {
                        content = String.format(
                                        "<p>Your job posting <strong>'%s'</strong> has been submitted for admin review.</p>"
                                                        +
                                                        "<p class='info-box'>Our team will review your job posting shortly to ensure it meets our community guidelines.</p>",
                                        jobTitle);
                }

                String htmlContent = buildHtmlEmail(subject, "Client", content, "View My Jobs",
                                "http://localhost:8080/client-dashboard/jobs");
                sendEmail(clientEmail, subject, htmlContent, true);
        }

        public void sendUserApprovalNotification(String userEmail, boolean approved) {
                String subject = approved ? "Account Approved" : "Account Under Review";
                String content;

                if (approved) {
                        content = "<p>Congratulations! Your account has been <span style='color: #28a745; font-weight: bold;'>APPROVED</span>.</p>"
                                        +
                                        "<p>You can now access all features of the platform. Welcome aboard!</p>";
                } else {
                        content = "<p>Your account has been submitted for review.</p>" +
                                        "<p class='info-box'>An admin will review your profile details shortly. You will be notified once your account is active.</p>";
                }

                String htmlContent = buildHtmlEmail(subject, "User", content, "Go to Dashboard",
                                "http://localhost:8080/dashboard/");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendRatingNotification(String freelancerEmail, Integer ratingValue, String clientEmail) {
                String subject = "New Rating Received - " + ratingValue + " Stars";
                String stars = "⭐".repeat(ratingValue);

                String content = String.format(
                                "<p>You have received a new rating from <strong>%s</strong>.</p>" +
                                                "<div style='font-size: 24px; text-align: center; margin: 20px 0;'>%s</div>"
                                                +
                                                "<p>This feedback helps build your reputation on the platform and attracts more clients. Keep up the excellent work!</p>",
                                clientEmail, stars);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Profile",
                                "http://localhost:8080/recruiter-profile/");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendProjectCompletionNotification(String freelancerEmail, String jobTitle, String clientEmail) {
                String subject = "Project Completed - " + jobTitle;
                String content = String.format(
                                "<p>Congratulations! Your project <strong>'%s'</strong> with <strong>%s</strong> has been marked as <span style='color: #28a745; font-weight: bold;'>COMPLETED</span>.</p>"
                                                +
                                                "<p>You can now view your earnings in your dashboard. The client may also leave a rating for your work.</p>",
                                jobTitle, clientEmail);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Earnings",
                                "http://localhost:8080/dashboard/");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendImportantUpdateNotification(String userEmail, String title, String message) {
                String subject = "Important Update - " + title;
                String content = String.format(
                                "<p>%s</p>" +
                                                "<div class='info-box'>%s</div>",
                                title, message);

                String htmlContent = buildHtmlEmail(subject, "User", content, "Login Now",
                                "http://localhost:8080/login");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendAdminVerificationNotification(String userEmail, String userType) {
                String subject = "New User Verification Required - " + userType;
                String content = String.format(
                                "<p>A new <strong>%s</strong> (%s) has uploaded verification documents and is ready for review.</p>"
                                                +
                                                "<p class='info-box'>Please review the submitted documents for authenticity.</p>",
                                userType, userEmail);

                String htmlContent = buildHtmlEmail(subject, "Admin", content, "Review Documents",
                                "http://localhost:8080/admin/users/pending");
                sendEmail("marefu933@gmail.com", subject, htmlContent, true);
        }

        public void sendJobModerationNotification(JobPostActivity job, String action, String reason) {
                String recruiterEmail = job.getPostedById().getEmail();
                String subject = "Job Posting Update - " + job.getJobTitle();
                String statusTitle = "";
                String statusDescription = "";
                String statusColor = "#333333";
                String nextSteps = "";

                switch (action) {
                        case "PAUSED":
                                statusTitle = "Paused";
                                statusDescription = "Your job post has been temporarily paused.";
                                statusColor = "#ffc107";
                                nextSteps = "You can resume the job at any time from your dashboard.";
                                break;
                        case "RESUMED":
                                statusTitle = "Resumed / Live";
                                statusDescription = "Your job post is now live and visible to freelancers again.";
                                statusColor = "#28a745";
                                nextSteps = "No action required. Good luck with your hiring!";
                                break;
                        case "UNDER_REVIEW":
                                statusTitle = "Under Review";
                                statusDescription = "Your job post has been flagged for a manual security review.";
                                statusColor = "#17a2b8";
                                nextSteps = "Our team will review the content within 24 hours.";
                                break;
                        case "VIOLATION":
                                statusTitle = "Violation Flagged";
                                statusDescription = "Your job post has been removed due to a policy violation.";
                                statusColor = "#dc3545";
                                nextSteps = "Please review our guidelines and edit your job post.";
                                break;
                        default:
                                statusTitle = action;
                                statusDescription = "The status of your job post has been updated.";
                }

                String content = String.format(
                                "<p>Notification for Job: <strong>'%s'</strong></p>" +
                                                "<div class='info-box' style='border-left-color: %s;'>" +
                                                "<h3>Status: <span style='color: %s;'>%s</span></h3>" +
                                                "<p>%s</p>" +
                                                (reason != null ? "<p><strong>Reason:</strong> " + reason + "</p>" : "")
                                                +
                                                "</div>" +
                                                "<p><strong>Next Steps:</strong> %s</p>",
                                job.getJobTitle(), statusColor, statusColor, statusTitle, statusDescription, nextSteps);

                String htmlContent = buildHtmlEmail(subject, "Client", content, "Manage Jobs",
                                "http://localhost:8080/client-dashboard/jobs");
                sendEmail(recruiterEmail, subject, htmlContent, true);
        }

        public void sendBanNotification(String userEmail, String userName, String reason) {
                String subject = "Account Suspended - Job Portal";
                String content = String.format(
                                "<p>Dear %s,</p>" +
                                                "<p>We writing to inform you that your account has been <strong>suspended</strong>.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #dc3545;'>" +
                                                "<h3>Reason for Suspension:</h3>" +
                                                "<p>%s</p>" +
                                                "</div>" +
                                                "<p>If you believe this is a mistake, please contact our support team.</p>",
                                userName, reason != null ? reason : "Violation of Terms of Service");

                String htmlContent = buildHtmlEmail(subject, userName, content, "Contact Support",
                                "mailto:support@jobportal.com");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendUnbanNotification(String userEmail, String userName) {
                String subject = "Account Restored - Job Portal";
                String content = String.format(
                                "<p>Dear %s,</p>" +
                                                "<p>Good news! Your account has been <strong>restored</strong> and is now active.</p>"
                                                +
                                                "<p>You can now log in and access all platform features.</p>",
                                userName);

                String htmlContent = buildHtmlEmail(subject, userName, content, "Login Now",
                                "http://localhost:8080/login");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendWelcomeNotification(String userEmail, String userName) {
                String subject = "Welcome to Jobify!";
                String content = String.format(
                                "<p>Dear %s,</p>" +
                                                "<p>Welcome to <strong>Jobify</strong>! We are thrilled to have you join our community.</p>"
                                                +
                                                "<p>Whether you are looking to hire top talent or find your next dream job, we are here to help you succeed.</p>"
                                                +
                                                "<p>To get started, please complete your profile to stand out.</p>",
                                userName);

                String htmlContent = buildHtmlEmail(subject, userName, content, "Go to Dashboard",
                                "http://localhost:8080/dashboard/");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendVerificationCompletionNotification(String userEmail, String userName) {
                String subject = "Verification Complete - Profile Verified";
                String content = String.format(
                                "<p>Dear %s,</p>" +
                                                "<p>Congratulations! Your identity verification documents have been reviewed and <span style='color: #28a745; font-weight: bold;'>APPROVED</span>.</p>"
                                                +
                                                "<p>Your profile now displays a <strong>Verified Badge</strong>, increasing trust with other users.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #28a745;'>You are now a verified member of the Jobify community!</div>",
                                userName);

                String htmlContent = buildHtmlEmail(subject, userName, content, "View Profile",
                                "http://localhost:8080/dashboard/");
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendContractStartedNotification(String freelancerEmail, String jobTitle, String clientName) {
                String subject = "Contract Started - " + jobTitle;
                String content = String.format(
                                "<p>Congratulations! A contract for the job <strong>'%s'</strong> has been started by <strong>%s</strong>.</p>"
                                                +
                                                "<p>The first milestone has been funded and is now in Escrow. You can begin working on the project.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #28a745;'>Status: <strong>Active</strong></div>",
                                jobTitle, clientName);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Contract",
                                "http://localhost:8080/dashboard/");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendWithdrawalNotification(String freelancerEmail, Double amount, String method) {
                String subject = "Withdrawal Processed Successfully";
                String content = String.format(
                                "<p>Your withdrawal request for <strong>$%s</strong> via <strong>%s</strong> has been processed successfully.</p>"
                                                +
                                                "<p>The funds are on their way to your connected account. Please allow standard processing time for the funds to appear.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #28a745;'>Expected Arrival: <strong>1-3 Business Days</strong></div>",
                                String.format("%.2f", amount), method);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Earnings",
                                "http://localhost:8080/freelancer-dashboard/earnings");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendWorkSubmittedNotification(String clientEmail, String jobTitle, String freelancerName) {
                String subject = "Work Submitted - " + jobTitle;
                String content = String.format(
                                "<p><strong>%s</strong> has submitted work for the job <strong>'%s'</strong>.</p>"
                                                +
                                                "<p>Please review the submission and approve it to release funds.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #0d6efd;'>Status: <strong>Submitted</strong></div>",
                                freelancerName, jobTitle);

                String htmlContent = buildHtmlEmail(subject, "Client", content, "Review Work",
                                "http://localhost:8080/contract/manage");
                sendEmail(clientEmail, subject, htmlContent, true);
        }

        public void sendWorkApprovedNotification(String freelancerEmail, String jobTitle, String clientName) {
                String subject = "Work Approved - " + jobTitle;
                String content = String.format(
                                "<p>Great news! <strong>%s</strong> has approved your work for the job <strong>'%s'</strong>.</p>"
                                                +
                                                "<p>The funds have been released to your available balance. You can now withdraw them to your Stripe account.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #28a745;'>Status: <strong>Approved & Paid</strong></div>",
                                clientName, jobTitle);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Earnings",
                                "http://localhost:8080/freelancer-dashboard/earnings");
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendWorkRejectedNotification(String freelancerEmail, String jobTitle, String clientName,
                        String reason, Integer contractId) {
                String subject = "Revision Requested - " + jobTitle;
                String content = String.format(
                                "<p><strong>%s</strong> has requested changes for the job <strong>'%s'</strong>.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #ffc107;'>" +
                                                "<h3>Feedback:</h3>" +
                                                "<p>%s</p>" +
                                                "</div>" +
                                                "<p>Please review the feedback and resubmit your work when ready.</p>"
                                                +
                                                "<div class='info-box' style='border-left-color: #ffc107;'>Status: <strong>In Progress (Revision)</strong></div>",
                                clientName, jobTitle, reason);

                String htmlContent = buildHtmlEmail(subject, "Freelancer", content, "View Contract",
                                "http://localhost:8080/contract/details/" + contractId);
                sendEmail(freelancerEmail, subject, htmlContent, true);
        }

        public void sendDisputeNotification(String adminEmail, String jobTitle, String clientEmail,
                        String freelancerEmail, String reason) {
                String subject = "Dispute Raised - " + jobTitle;
                String content = String.format(
                                "<p>A dispute has been raised for the job <strong>'%s'</strong>.</p>" +
                                                "<ul>" +
                                                "<li><strong>Client:</strong> %s</li>" +
                                                "<li><strong>Freelancer:</strong> %s</li>" +
                                                "</ul>" +
                                                "<div class='info-box' style='border-left-color: #dc3545;'>" +
                                                "<h3>Reason:</h3>" +
                                                "<p>%s</p>" +
                                                "</div>" +
                                                "<p>Please review the contract details and intervene.</p>",
                                jobTitle, clientEmail, freelancerEmail, reason);

                String htmlContent = buildHtmlEmail(subject, "Admin", content, "Review Contract",
                                "http://localhost:8080/admin/contracts"); // Placeholder Admin URL
                sendEmail(adminEmail, subject, htmlContent, true);
        }

        public void sendPasswordResetEmail(String userEmail, String resetLink) {
                String subject = "Password Reset Request";
                String content = "<p>We received a request to reset the password for your Jobify account.</p>" +
                                "<p>If you made this request, please click the button below to set a new password. " +
                                "This link will expire in 24 hours.</p>" +
                                "<p class='info-box' style='border-left-color: #ffc107;'>" +
                                "If you did not request a password reset, you can safely ignore this email.</p>";

                String htmlContent = buildHtmlEmail(subject, "User", content, "Reset Password", resetLink);
                sendEmail(userEmail, subject, htmlContent, true);
        }

        public void sendVerificationEmail(String userEmail, String userName, String verificationLink) {
                String subject = "Verify your Jobify Account";
                String content = "<p>Welcome to Jobify!</p>" +
                                "<p>Before you can log in and start using your account, we need to verify your email address.</p>" +
                                "<p>Please click the button below to verify your email. " +
                                "This link will expire in 5 minutes.</p>" +
                                "<p class='info-box' style='border-left-color: #28a745;'>" +
                                "If you did not create this account, you can safely ignore this email.</p>";

                String htmlContent = buildHtmlEmail(subject, userName, content, "Verify Email Address", verificationLink);
                sendEmail(userEmail, subject, htmlContent, true);
        }
}
