package com.localdelivery.service;

import java.net.URI;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountMailService {
    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);
    private final AccountTokenService tokens;
    private final JavaMailSender sender;
    private final Executor executor;
    private final boolean enabled;
    private final String baseUrl;
    private final String from;
    public AccountMailService(AccountTokenService tokens, JavaMailSender sender,
            @Qualifier("accountMailExecutor") Executor executor,
            @Value("${app.accounts.mail-enabled:true}") boolean enabled,
            @Value("${app.public-base-url}") String baseUrl, @Value("${app.mail-from}") String from) {
        var uri = URI.create(baseUrl);
        boolean local = "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getPath().matches("/?"))
            throw new IllegalArgumentException("PUBLIC_BASE_URL must be an HTTPS origin (or loopback HTTP for development).");
        this.tokens = tokens; this.sender = sender; this.executor = executor; this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/$", ""); this.from = from;
    }
    public void request(String email, AccountTokenService.Purpose purpose) {
        afterCommit(() -> tokens.issue(email, purpose).ifPresent(item -> {
            boolean verify = purpose == AccountTokenService.Purpose.VERIFY_EMAIL;
            send(item.email(), verify ? "Verify your Local Delivery email" : "Reset your Local Delivery password",
                    (verify ? "Confirm your email using the link below. It expires in 24 hours."
                            : "Choose a new password using the link below. It expires in 30 minutes.")
                    + "\n\n" + baseUrl + (verify ? "/verify-email" : "/reset-password") + "#token=" + item.token()
                    + "\n\nIf you did not request this email, you can ignore it.");
        }));
    }
    public void passwordChanged(String email) {
        afterCommit(() -> send(email, "Your Local Delivery password changed",
                "Your password has been changed and previous sessions have been signed out. If this was not you, request another password reset from " + baseUrl + "/forgot-password"));
    }
    private void send(String email, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject(subject); message.setText(text);
        sender.send(message);
    }
    private void afterCommit(Runnable operation) {
        if (!enabled) return;
        Runnable submit = () -> {
            try {
                executor.execute(() -> {
                    try { operation.run(); }
                    catch (Exception error) { log.error("Account email delivery failed; request a new email. Failure type: {}", error.getClass().getSimpleName()); }
                });
            } catch (RuntimeException error) { log.error("Account email queue is full; request a new email."); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
    }
}
