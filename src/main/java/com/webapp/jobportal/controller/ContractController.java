package com.webapp.jobportal.controller;

import com.stripe.exception.StripeException;
import com.webapp.jobportal.entity.*;
import com.webapp.jobportal.repository.*;
import com.webapp.jobportal.services.StripeService;
import com.webapp.jobportal.util.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.stripe.model.PaymentIntent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Value;
import com.webapp.jobportal.services.NotificationService;
import com.webapp.jobportal.services.EmailService;

import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/contract")
public class ContractController {

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private JobSeekerApplyRepository jobSeekerApplyRepository;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Value("${stripe.public.key}")
    private String stripePublicKey;

    @PostMapping("/hire/{applicationId}")
    public String hireFreelancer(@PathVariable Integer applicationId, Model model,
            RedirectAttributes redirectAttributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return "redirect:/login";
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Users client = usersRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        JobSeekerApply application = jobSeekerApplyRepository.findById(applicationId).orElseThrow();
        Users freelancer = application.getUserId().getUserId();

        // Check if contract already exists
        Contract contract = contractRepository.findByJobApplicationId(applicationId).orElse(null);

        if (contract == null) {
            // Create Contract (Draft)
            contract = new Contract(application, client, freelancer,
                    "Contract for " + application.getJob().getJobTitle(), application.getProposedRate(), "DRAFT");
            contract = contractRepository.save(contract);
        }

        // Check if Initial Milestone already exists
        // simplified check: if contract has milestones, assume initial one exists (or
        // we could fetch by description/status)
        Milestone milestone = null;
        if (!contract.getMilestones().isEmpty()) {
            // Get the first one for now (or find the PENDING one)
            milestone = contract.getMilestones().get(0);
        } else {
            // Create Initial Milestone
            milestone = new Milestone(contract, "Initial Milestone", application.getProposedRate(), "PENDING");
            milestone = milestoneRepository.save(milestone);
        }

        try {
            // Create Stripe PaymentIntent for Embedded Checkout
            PaymentIntent paymentIntent = stripeService.createPaymentIntent(milestone);
            milestone.setStripeSessionId(paymentIntent.getId()); // Saving Intent ID here for tracking
            milestoneRepository.save(milestone);

            // Ensure Payment record exists with status PENDING for this milestone
            Payment payment = paymentRepository.findByStripeSessionId(paymentIntent.getId());
            if (payment == null) {
                payment = new Payment();
                payment.setPayer(client);
                payment.setPayee(freelancer);
                payment.setJob(application.getJob());
                payment.setMilestone(milestone);
                payment.setAmount(milestone.getAmount());
                payment.setServiceFee(milestone.getAmount() * 0.10);
                payment.setTotalAmount(milestone.getAmount());
                payment.setPaymentDate(new Date());
                payment.setPaymentMethod("Stripe Escrow");
                payment.setStatus("PENDING");
                payment.setStripeSessionId(paymentIntent.getId());
                paymentRepository.save(payment);
            }

            model.addAttribute("clientSecret", paymentIntent.getClientSecret());
            model.addAttribute("stripePublicKey", stripePublicKey);
            model.addAttribute("milestone", milestone);
            model.addAttribute("contract", contract);

            return "contract-checkout";
        } catch (StripeException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Payment initiation failed: " + e.getMessage());
            return "redirect:/job-details-apply/" + application.getJob().getJobPostId();
        }
    }

    @GetMapping("/manage")
    public String manageContracts(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return "redirect:/login";
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Users user = usersRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        List<Contract> contracts;
        if (user.getUserTypeId().getUserTypeId() == 1) { // Recruiter/Client
            contracts = contractRepository.findByClientUserId(user.getUserId());
        } else { // Job Seeker/Freelancer
            contracts = contractRepository.findByFreelancerUserId(user.getUserId());
        }

        model.addAttribute("contracts", contracts);

        // Split into Active and Archived
        List<Contract> activeContracts = new java.util.ArrayList<>();
        List<Contract> archivedContracts = new java.util.ArrayList<>();

        for (Contract c : contracts) {
            String status = c.getStatus();
            if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "REFUNDED".equals(status)) {
                archivedContracts.add(c);
            } else {
                activeContracts.add(c);
            }
        }

        model.addAttribute("activeContracts", activeContracts);
        model.addAttribute("archivedContracts", archivedContracts);

        model.addAttribute("userType", user.getUserTypeId().getUserTypeId());
        return "contract-manage";
    }

    @PostMapping("/end/{contractId}")
    public String endContract(@PathVariable Integer contractId, RedirectAttributes redirectAttributes) {
        Contract contract = contractRepository.findById(contractId).orElseThrow();

        // Verification: Ensure all milestones are handled?
        // For now, we allow explicit ending if at least one milestone is approved or if
        // it's mutual.
        // Simplification: Client can end it.

        contract.setStatus("COMPLETED");
        contractRepository.save(contract);

        // Notify Freelancer
        notificationService.createNotification(
                contract.getFreelancer(),
                "Contract Completed: " + contract.getTitle(),
                "The client has ended the contract. Great job!",
                "CONTRACT",
                contract.getId());

        redirectAttributes.addFlashAttribute("success", "Contract ended and moved to history.");
        return "redirect:/contract/details/" + contractId;
    }

    @GetMapping("/details/{contractId}")
    public String contractDetails(@PathVariable Integer contractId, Model model) {
        Contract contract = contractRepository.findById(contractId).orElseThrow();
        model.addAttribute("contract", contract);
        return "contract-details"; // We need to create this view
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/milestone/start/{milestoneId}")
    public String startWork(@PathVariable Integer milestoneId, RedirectAttributes redirectAttributes) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow();
        if (!"FUNDED".equals(milestone.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Milestone must be funded before starting work.");
            return "redirect:/contract/details/" + milestone.getContract().getId();
        }
        milestone.setStatus("IN_PROGRESS");
        milestoneRepository.save(milestone);

        Contract contract = milestone.getContract();
        Users client = contract.getClient();
        Users freelancer = contract.getFreelancer();

        notificationService.createNotification(
                client,
                "Work Started: " + contract.getJobApplication().getJob().getJobTitle(),
                freelancer.getFirstName() + " has started working on milestone: " + milestone.getDescription(),
                "CONTRACT",
                contract.getId());

        redirectAttributes.addFlashAttribute("success", "You have started work. Submit when ready for client review.");
        return "redirect:/contract/details/" + contract.getId();
    }

    @PostMapping("/milestone/submit/{milestoneId}")
    public String submitWork(@PathVariable Integer milestoneId, RedirectAttributes redirectAttributes) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow();
        String status = milestone.getStatus();
        if (!"FUNDED".equals(status) && !"IN_PROGRESS".equals(status)) {
            redirectAttributes.addFlashAttribute("error", "Start work first, then submit when ready for review.");
            return "redirect:/contract/details/" + milestone.getContract().getId();
        }

        milestone.setStatus("SUBMITTED");
        milestone.setDueDate(new Date()); // Using dueDate as submitted date for now
        milestoneRepository.save(milestone);

        // Notify Client
        Contract contract = milestone.getContract();
        Users client = contract.getClient();
        Users freelancer = contract.getFreelancer();

        notificationService.createNotification(
                client,
                "Work Submitted: " + contract.getJobApplication().getJob().getJobTitle(),
                freelancer.getFirstName() + " has submitted work for milestone: " + milestone.getDescription(),
                "CONTRACT",
                contract.getId());

        emailService.sendWorkSubmittedNotification(
                client.getEmail(),
                contract.getJobApplication().getJob().getJobTitle(),
                freelancer.getFirstName() + " " + freelancer.getLastName());

        redirectAttributes.addFlashAttribute("success", "Work submitted successfully. Client has been notified.");
        return "redirect:/contract/details/" + contract.getId();
    }

    @PostMapping("/milestone/approve/{milestoneId}")
    public String approveWork(@PathVariable Integer milestoneId, RedirectAttributes redirectAttributes) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow();

        // 1. Update Statuses
        // Client has approved; funds are now available in freelancer's platform balance.
        milestone.setStatus("AVAILABLE_BALANCE");
        milestoneRepository.save(milestone);

        Contract contract = milestone.getContract();
        contract.setStatus("ACTIVE");
        contractRepository.save(contract);

        // 2. Update existing Payment or record new Payment (funds released from escrow to freelancer's Available Balance on platform)
        Payment payment = null;
        if (milestone.getStripeSessionId() != null) {
            payment = paymentRepository.findByStripeSessionId(milestone.getStripeSessionId());
        }
        if (payment == null) {
            List<Payment> jobPayments = paymentRepository.findByJob(contract.getJobApplication().getJob());
            payment = jobPayments.stream()
                    .filter(p -> p.getMilestone() != null && p.getMilestone().getId().equals(milestone.getId()))
                    .findFirst().orElse(null);
        }

        if (payment != null) {
            payment.setStatus("COMPLETED");
            payment.setPaymentDate(new Date());
            paymentRepository.save(payment);
        } else {
            payment = new Payment(
                    contract.getClient(),
                    contract.getFreelancer(),
                    contract.getJobApplication().getJob(),
                    milestone.getAmount(),
                    milestone.getAmount() * 0.10,
                    milestone.getAmount(),
                    "Stripe Escrow",
                    "COMPLETED");
            payment.setMilestone(milestone);
            paymentRepository.save(payment);
        }

        // Notify Freelancer
        notificationService.createNotification(
                contract.getFreelancer(),
                "Work Approved: " + contract.getJobApplication().getJob().getJobTitle(),
                "Your work for milestone '" + milestone.getDescription() + "' has been approved. Funds are released.",
                "PAYMENT",
                contract.getId());

        emailService.sendWorkApprovedNotification(
                contract.getFreelancer().getEmail(),
                contract.getJobApplication().getJob().getJobTitle(),
                contract.getClient().getFirstName() + " " + contract.getClient().getLastName());

        redirectAttributes.addFlashAttribute("success",
                "Work approved. Funds are now available for freelancer withdrawal.");

        return "redirect:/contract/details/" + milestone.getContract().getId();
    }

    @PostMapping("/milestone/reject/{milestoneId}")
    public String rejectWork(@PathVariable Integer milestoneId, @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow();

        if (!"SUBMITTED".equals(milestone.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Can only request revision for submitted work.");
            return "redirect:/contract/details/" + milestone.getContract().getId();
        }

        // 1. Revert Status
        milestone.setStatus("IN_PROGRESS");
        milestoneRepository.save(milestone);

        Contract contract = milestone.getContract();

        // 2. Notify Freelancer
        notificationService.createNotification(
                contract.getFreelancer(),
                "Revision Requested: " + contract.getJobApplication().getJob().getJobTitle(),
                "Client has requested changes: " + reason,
                "CONTRACT",
                contract.getId());

        emailService.sendWorkRejectedNotification(
                contract.getFreelancer().getEmail(),
                contract.getJobApplication().getJob().getJobTitle(),
                contract.getClient().getFirstName() + " " + contract.getClient().getLastName(),
                reason,
                contract.getId());

        redirectAttributes.addFlashAttribute("info", "Revision requested. Freelancer has been notified.");

        return "redirect:/contract/details/" + milestone.getContract().getId();
    }

    @PostMapping("/dispute/{contractId}")
    public String raiseDispute(@PathVariable Integer contractId, @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {
        Contract contract = contractRepository.findById(contractId).orElseThrow();

        // 1. Update Contract Status
        contract.setStatus("DISPUTED");
        contractRepository.save(contract);

        // 1b. Freeze milestone workflow while disputed
        // (Funds are held; no approval/withdrawal should proceed until admin decision.)
        for (Milestone m : contract.getMilestones()) {
            String s = m.getStatus();
            if ("FUNDED".equals(s) || "IN_PROGRESS".equals(s) || "SUBMITTED".equals(s)) {
                m.setStatus("DISPUTED");
                milestoneRepository.save(m);
            }
        }

        // 2. Notify Admin (simulated via email or just log/notification)
        // In a real app, this would go to an admin dashboard
        emailService.sendDisputeNotification("admin@jobportal.com", // Admin email
                contract.getJobApplication().getJob().getJobTitle(),
                contract.getClient().getEmail(),
                contract.getFreelancer().getEmail(),
                reason);

        // 3. Notify Both Parties
        notificationService.createNotification(
                contract.getClient(),
                "Dispute Raised: " + contract.getJobApplication().getJob().getJobTitle(),
                "A dispute has been raised on this contract. Admin will review.",
                "CONTRACT",
                contract.getId());

        notificationService.createNotification(
                contract.getFreelancer(),
                "Dispute Raised: " + contract.getJobApplication().getJob().getJobTitle(),
                "A dispute has been raised on this contract. Admin will review.",
                "CONTRACT",
                contract.getId());

        redirectAttributes.addFlashAttribute("error", "Dispute raised. Admin has been notified.");

        return "redirect:/contract/details/" + contractId;
    }

    @PostMapping("/milestone/refund/{milestoneId}")
    public String refundMilestone(@PathVariable Integer milestoneId, RedirectAttributes redirectAttributes) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow();

        // Check if refundable
        if (!"FUNDED".equals(milestone.getStatus()) && !"DISPUTED".equals(milestone.getStatus())) {
            redirectAttributes.addFlashAttribute("error",
                    "Cannot refund this milestone. It may not be funded or is not in a refundable state.");
            return "redirect:/contract/details/" + milestone.getContract().getId();
        }

        try {
            // 1. Process Refund via Stripe
            if (milestone.getStripeSessionId() != null) {
                // If it's a PaymentIntent ID (which we stored in stripeSessionId for Embedded)
                stripeService.refundPayment(milestone.getStripeSessionId());
            } else {
                // Fallback or error if no ID
                // For now, proceed to update status if manual/simulated
            }

            // 2. Update Status
            milestone.setStatus("REFUNDED");
            milestoneRepository.save(milestone);

            // Update Payment Record
            // Payment payment = paymentRepository.findByStripeSessionId... (omitted for
            // brevity, assume synced)

            // 3. Notify Client
            notificationService.createNotification(
                    milestone.getContract().getClient(),
                    "Refund Processed",
                    "Funds for milestone '" + milestone.getDescription() + "' have been refunded.",
                    "PAYMENT",
                    milestone.getContract().getId());

            redirectAttributes.addFlashAttribute("success", "Refund processed successfully.");

        } catch (StripeException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Refund failed: " + e.getMessage());
        }

        return "redirect:/contract/details/" + milestone.getContract().getId();
    }
}
