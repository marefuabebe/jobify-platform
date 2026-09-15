package com.webapp.jobportal.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.PaymentIntent;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StripeServiceTest {

    @BeforeAll
    static void setUp() {
        Stripe.apiKey = System.getenv().getOrDefault("STRIPE_API_KEY", "mock_stripe_api_key");
    }

    @Test
    void testPaymentIntentCreation() throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(2500L) // $25.00
                .setCurrency("usd")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .putMetadata("test", "escrow_flow_verification")
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        assertNotNull(intent);
        assertNotNull(intent.getId());
        assertNotNull(intent.getClientSecret());
        System.out.println(">>> SUCCESS: PaymentIntent created: " + intent.getId());
    }

    @Test
    void testStripeConnectAccountCreation() {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("US")
                    .setEmail("testfreelancer@example.com")
                    .setCapabilities(
                            AccountCreateParams.Capabilities.builder()
                                    .setCardPayments(
                                            AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                                    .setTransfers(
                                            AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                    .build())
                    .build();

            Account account = Account.create(params);
            assertNotNull(account);
            System.out.println(">>> SUCCESS: Stripe Connect Express Account created: " + account.getId());
        } catch (StripeException e) {
            System.err.println(">>> STRIPE CONNECT CHECK: " + e.getMessage());
            // Note if connect is enabled or requires onboarding on the dashboard
        }
    }
}