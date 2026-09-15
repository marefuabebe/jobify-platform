package com.webapp.jobportal.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.HashMap;
import java.util.Map;
import com.webapp.jobportal.entity.JobPostActivity;
import com.webapp.jobportal.entity.JobSeekerApply;
import com.webapp.jobportal.entity.Payment;
import com.webapp.jobportal.entity.Users;
import com.webapp.jobportal.repository.PaymentRepository;
import com.webapp.jobportal.services.JobPostActivityService;
import com.webapp.jobportal.services.UsersService;
import com.webapp.jobportal.services.JobSeekerApplyService;
import com.webapp.jobportal.services.EmailService;
import com.webapp.jobportal.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;

@Controller
public class PaymentController {

    @Value("${stripe.public.key}")
    private String stripePublicKey;

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    private final JobPostActivityService jobPostActivityService;
    private final JobSeekerApplyService jobSeekerApplyService;
    private final UsersService usersService;
    private final PaymentRepository paymentRepository;
    private final com.webapp.jobportal.repository.MilestoneRepository milestoneRepository;
    private final com.webapp.jobportal.repository.ContractRepository contractRepository;
    private final com.webapp.jobportal.services.EmailService emailService;
    private final NotificationService notificationService;

    @Autowired
    public PaymentController(JobPostActivityService jobPostActivityService, UsersService usersService,
            PaymentRepository paymentRepository,
            com.webapp.jobportal.repository.MilestoneRepository milestoneRepository,
            com.webapp.jobportal.repository.ContractRepository contractRepository,
            com.webapp.jobportal.services.JobSeekerApplyService jobSeekerApplyService,
            com.webapp.jobportal.services.EmailService emailService,
            NotificationService notificationService) {
        this.jobPostActivityService = jobPostActivityService;
        this.usersService = usersService;
        this.paymentRepository = paymentRepository;
        this.milestoneRepository = milestoneRepository;
        this.contractRepository = contractRepository;
        this.jobSeekerApplyService = jobSeekerApplyService;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/payment/checkout", method = {
            org.springframework.web.bind.annotation.RequestMethod.GET,
            org.springframework.web.bind.annotation.RequestMethod.POST })
    public String createCheckoutSession(@RequestParam("jobId") Integer jobId,
            @RequestParam("milestoneId") Integer milestoneId,
            @RequestParam("amount") Double amount,
            RedirectAttributes redirectAttributes) {
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        JobPostActivity job = jobPostActivityService.getOne(jobId);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
            return "redirect:/client-dashboard/";
        }

        // Verify Milestone exists
        com.webapp.jobportal.entity.Milestone milestone = milestoneRepository.findById(milestoneId).orElse(null);
        if (milestone == null) {
            redirectAttributes.addFlashAttribute("error", "Milestone not found.");
            return "redirect:/contract/details/" + (job.getPostedById() != null ? job.getJobPostId() : "");
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(
                            baseUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}&milestone_id=" + milestoneId)
                    .setCancelUrl(baseUrl + "/payment/cancel")
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount((long) (amount * 100)) // Amount in cents
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Payment for Job: " + job.getJobTitle())
                                                                    .build())
                                                    .build())
                                    .build())
                    .setClientReferenceId(String.valueOf(currentUser.getUserId()))
                    .build();

            Session session = Session.create(params);

            // Create preliminary payment record
            Payment payment = paymentRepository.findByStripeSessionId(session.getId());
            if (payment == null) {
                payment = new Payment();
                payment.setPayer(currentUser);
                if (milestone.getContract() != null) {
                    payment.setPayee(milestone.getContract().getFreelancer());
                }
                payment.setJob(job);
                payment.setMilestone(milestone);
                payment.setAmount(amount);
                payment.setServiceFee(amount * 0.10); // 10% Platform Fee
                payment.setTotalAmount(amount); // Client pays full amount, we deduct fee before transfer
                payment.setPaymentDate(new Date());
                payment.setPaymentMethod("Stripe Checkout");
                payment.setStatus("PENDING");
                payment.setStripeSessionId(session.getId());
                paymentRepository.save(payment);
            }

            return "redirect:" + session.getUrl();

        } catch (StripeException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error initiating payment: " + e.getMessage());
            return "redirect:/client-dashboard/";
        }
    }

    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam(value = "session_id", required = false) String sessionId,
            @RequestParam(value = "payment_intent", required = false) String paymentIntentId,
            @RequestParam(value = "milestone_id", required = false) Integer milestoneId,
            RedirectAttributes redirectAttributes) {
        try {
            if (sessionId != null) {
                Session session = Session.retrieve(sessionId);
                if ("paid".equals(session.getPaymentStatus())) {
                    Payment payment = paymentRepository.findByStripeSessionId(sessionId);
                    if (payment == null && milestoneId != null) {
                        com.webapp.jobportal.entity.Milestone m = milestoneRepository.findById(milestoneId).orElse(null);
                        if (m != null && m.getContract() != null) {
                            payment = new Payment();
                            payment.setPayer(m.getContract().getClient());
                            payment.setPayee(m.getContract().getFreelancer());
                            payment.setJob(m.getContract().getJobApplication().getJob());
                            payment.setMilestone(m);
                            payment.setAmount(m.getAmount());
                            payment.setServiceFee(m.getAmount() * 0.10);
                            payment.setTotalAmount(m.getAmount());
                            payment.setPaymentDate(new Date());
                            payment.setPaymentMethod("Stripe Checkout");
                            payment.setStripeSessionId(sessionId);
                        }
                    }
                    if (payment != null) {
                        payment.setStatus("ESCROW_HELD");
                        paymentRepository.save(payment);
                        redirectAttributes.addFlashAttribute("success",
                                "Payment secured in Escrow! Please wait for work to be submitted.");
                    }
                }
            } else if (paymentIntentId != null) {
                PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
                if ("succeeded".equals(intent.getStatus())) {
                    Payment payment = paymentRepository.findByStripeSessionId(paymentIntentId);
                    if (payment == null && milestoneId != null) {
                        com.webapp.jobportal.entity.Milestone m = milestoneRepository.findById(milestoneId).orElse(null);
                        if (m != null && m.getContract() != null) {
                            payment = new Payment();
                            payment.setPayer(m.getContract().getClient());
                            payment.setPayee(m.getContract().getFreelancer());
                            payment.setJob(m.getContract().getJobApplication().getJob());
                            payment.setMilestone(m);
                            payment.setAmount(m.getAmount());
                            payment.setServiceFee(m.getAmount() * 0.10);
                            payment.setTotalAmount(m.getAmount());
                            payment.setPaymentDate(new Date());
                            payment.setPaymentMethod("Stripe Escrow");
                            payment.setStripeSessionId(paymentIntentId);
                        }
                    }
                    if (payment != null) {
                        payment.setStatus("ESCROW_HELD");
                        paymentRepository.save(payment);
                        redirectAttributes.addFlashAttribute("success",
                                "Payment secured in Escrow! Please wait for work to be submitted.");
                    }
                }
            }

            if (milestoneId != null) {
                com.webapp.jobportal.entity.Milestone milestone = milestoneRepository.findById(milestoneId)
                        .orElse(null);
                if (milestone != null) {
                    milestone.setStatus("FUNDED");
                    milestoneRepository.save(milestone);

                    if ("DRAFT".equals(milestone.getContract().getStatus())) {
                        milestone.getContract().setStatus("ACTIVE");
                        contractRepository.save(milestone.getContract());

                        // Update Application Status and Send Notification
                        JobSeekerApply application = milestone.getContract().getJobApplication();
                        if (application != null && !"HIRED".equals(application.getApplicationStatus())) {
                            jobSeekerApplyService.updateApplicationStatus(application.getId(), "HIRED");

                            // Send Email Notification
                            if (application.getUserId() != null && application.getUserId().getUserId() != null) {
                                String freelancerEmail = application.getUserId().getUserId().getEmail();
                                String jobTitle = application.getJob().getJobTitle();
                                String clientName = "The Client";
                                if (application.getJob().getPostedById() != null) {
                                    clientName = application.getJob().getPostedById().getFirstName() + " "
                                            + application.getJob().getPostedById().getLastName();
                                }
                                emailService.sendContractStartedNotification(freelancerEmail, jobTitle, clientName);

                                // Send In-App Notification
                                notificationService.createNotification(
                                        application.getUserId().getUserId(),
                                        "Contract Started",
                                        "You have been hired for " + jobTitle + ". Work can now begin.",
                                        "CONTRACT",
                                        milestone.getContract().getId());
                            }
                        }
                    }

                    redirectAttributes.addFlashAttribute("success",
                            "Milestone funded successfully! Money is now in Escrow.");
                    return "redirect:/contract/details/" + milestone.getContract().getId();
                }
            }
        } catch (StripeException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error verifying payment.");
        }
        return "redirect:/client-dashboard/payments";
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("info", "Payment cancelled.");
        return "redirect:/client-dashboard/";
    }

    @PostMapping("/payment/create-payment-intent")
    @ResponseBody
    public Map<String, String> createPaymentIntent(@RequestParam("jobId") Integer jobId,
            @RequestParam("milestoneId") Integer milestoneId,
            @RequestParam("amount") Double amount) {
        Map<String, String> response = new HashMap<>();
        Users currentUser = usersService.getCurrentUser();
        if (currentUser == null) {
            response.put("error", "User not logged in");
            return response;
        }

        JobPostActivity job = jobPostActivityService.getOne(jobId);
        if (job == null) {
            response.put("error", "Job not found");
            return response;
        }

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long) (amount * 100)) // Amount in cents
                    .setCurrency("usd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .putMetadata("job_id", String.valueOf(jobId))
                    .putMetadata("user_id", String.valueOf(currentUser.getUserId()))
                    .putMetadata("milestone_id", String.valueOf(milestoneId))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            // Create preliminary payment record
            Payment payment = new Payment();
            payment.setPayer(currentUser);
            payment.setJob(job);
            payment.setAmount(amount);
            payment.setServiceFee(amount * 0.10); // 10% Platform Fee
            payment.setTotalAmount(amount);
            payment.setPaymentDate(new Date());
            payment.setPaymentMethod("Stripe Embedded");
            payment.setStatus("PENDING");
            payment.setStripeSessionId(intent.getId()); // Use Intent ID

            paymentRepository.save(payment);

            response.put("clientSecret", intent.getClientSecret());
            return response;

        } catch (StripeException e) {
            e.printStackTrace();
            response.put("error", e.getMessage());
            return response;
        }
    }

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @PostMapping("/payment/webhook")
    @ResponseBody
    public String handleStripeWebhook(@org.springframework.web.bind.annotation.RequestBody String payload,
            @org.springframework.web.bind.annotation.RequestHeader("Stripe-Signature") String sigHeader) {

        if (endpointSecret == null || endpointSecret.isEmpty()) {
            return "Webhook secret not configured";
        }

        com.stripe.model.Event event;

        try {
            event = com.stripe.net.Webhook.constructEvent(
                    payload, sigHeader, endpointSecret);
        } catch (StripeException e) {
            // Invalid signature
            return "Webhook error: " + e.getMessage();
        }

        // Deserialize the nested object inside the event
        com.stripe.model.EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        com.stripe.model.StripeObject stripeObject = null;
        if (dataObjectDeserializer.getObject().isPresent()) {
            stripeObject = dataObjectDeserializer.getObject().get();
        } else {
            // Deserialization failed, probably due to API version mismatch.
            // Refer to the API docs for how to handle this.
            return "Deserialization failed";
        }

        // Handle the event
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) stripeObject;
            // Fulfill the purchase, update payment status, etc.
            // Logic duplicated from paymentSuccess for robustness
            Payment payment = paymentRepository.findByStripeSessionId(session.getId());
            if (payment != null) {
                payment.setStatus("ESCROW_HELD");
                paymentRepository.save(payment);
            }
        } else if ("payment_intent.succeeded".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) stripeObject;
            Payment payment = paymentRepository.findByStripeSessionId(intent.getId());
            if (payment != null) {
                payment.setStatus("ESCROW_HELD");
                paymentRepository.save(payment);
            } else {
                // If payment record not found (milestone payment), check milestone
                String milestoneIdStr = intent.getMetadata().get("milestone_id");
                if (milestoneIdStr != null) {
                    Integer milestoneId = Integer.parseInt(milestoneIdStr);
                    com.webapp.jobportal.entity.Milestone milestone = milestoneRepository.findById(milestoneId)
                            .orElse(null);
                    if (milestone != null) {
                        milestone.setStatus("FUNDED");
                        milestoneRepository.save(milestone);

                        if ("DRAFT".equals(milestone.getContract().getStatus())) {
                            milestone.getContract().setStatus("ACTIVE");
                            contractRepository.save(milestone.getContract());

                            // Update Application Status and Send Notification
                            JobSeekerApply application = milestone.getContract().getJobApplication();
                            if (application != null && !"HIRED".equals(application.getApplicationStatus())) {
                                jobSeekerApplyService.updateApplicationStatus(application.getId(), "HIRED");

                                // Send Email Notification
                                if (application.getUserId() != null && application.getUserId().getUserId() != null) {
                                    String freelancerEmail = application.getUserId().getUserId().getEmail();
                                    String jobTitle = application.getJob().getJobTitle();
                                    String clientName = "The Client";
                                    if (application.getJob().getPostedById() != null) {
                                        clientName = application.getJob().getPostedById().getFirstName() + " "
                                                + application.getJob().getPostedById().getLastName();
                                    }
                                    emailService.sendContractStartedNotification(freelancerEmail, jobTitle, clientName);

                                    // Send In-App Notification
                                    notificationService.createNotification(
                                            application.getUserId().getUserId(),
                                            "Contract Started",
                                            "You have been hired for " + jobTitle + ". Work can now begin.",
                                            "CONTRACT",
                                            milestone.getContract().getId());
                                }
                            }
                        }
                    }
                }
            }
        }

        return "ok";
    }
}
