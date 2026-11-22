package com.propio.service;

import com.propio.model.*;
import com.propio.repository.EmailLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailService Unit Tests")
class EmailServiceTest {

    private EmailService emailService;
    private StubEmailLogRepository stubRepository;

    @BeforeEach
    void setUp() {
        stubRepository = new StubEmailLogRepository();

        ObjectProvider<JavaMailSender> emptyProvider = new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject(Object... args) { return null; }
            @Override
            public JavaMailSender getIfAvailable() { return null; }
            @Override
            public JavaMailSender getIfUnique() { return null; }
            @Override
            public JavaMailSender getObject() { return null; }
        };

        SpringTemplateEngine dummyEngine = new SpringTemplateEngine();
        PdfGenerationService pdfService = new PdfGenerationService();

        emailService = new EmailService(
                emptyProvider,
                dummyEngine,
                stubRepository,
                pdfService
        );

        ReflectionTestUtils.setField(emailService, "mailEnabled", false);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@propio.com");
        ReflectionTestUtils.setField(emailService, "fromName", "Propio Property Management");
        ReflectionTestUtils.setField(emailService, "maxRetryAttempts", 3);
    }

    @Test
    @DisplayName("Safe fallback records SIMULATED_SENT status when mailEnabled=false")
    void testDispatchAsyncFallbackMode() throws Exception {
        CompletableFuture<EmailLog> future = emailService.dispatchAsync(
                "tenant@example.com",
                "Rent Due Reminder",
                EmailType.RENT_DUE_SOON,
                "<html><body>Hello</body></html>",
                901L,
                null,
                null
        );

        EmailLog result = future.get();
        assertNotNull(result);
        assertEquals(EmailStatus.SIMULATED_SENT, result.getStatus());
        assertEquals("tenant@example.com", result.getRecipient());
        assertEquals("Rent Due Reminder", result.getSubject());
        assertEquals(EmailType.RENT_DUE_SOON, result.getEmailType());
        assertNotNull(result.getSentAt());
        assertFalse(result.isHasAttachment());
        assertTrue(stubRepository.savedLogs.contains(result));
    }

    @Test
    @DisplayName("Duplicate protection check correctly identifies sent emails")
    void testDuplicateProtectionCheck() {
        EmailLog logged = EmailLog.builder()
                .id(1L)
                .recipient("test@propio.com")
                .subject("Test")
                .emailType(EmailType.RENT_DUE_SOON)
                .referenceId(901L)
                .status(EmailStatus.SIMULATED_SENT)
                .build();
        stubRepository.savedLogs.add(logged);

        assertTrue(stubRepository.hasSentEmail(EmailType.RENT_DUE_SOON, 901L));
        assertFalse(stubRepository.hasSentEmail(EmailType.RENT_DUE_SOON, 902L));
        assertFalse(stubRepository.hasSentEmail(EmailType.RENT_OVERDUE, 901L));
    }

    @Test
    @DisplayName("Email with attachment sets hasAttachment=true and stores filename")
    void testEmailWithAttachment() throws Exception {
        byte[] dummyAttachment = new byte[]{1, 2, 3, 4, 5};
        CompletableFuture<EmailLog> future = emailService.dispatchAsync(
                "tenant@example.com",
                "Payment Receipt",
                EmailType.PAYMENT_RECEIVED,
                "<html><body>Receipt Body</body></html>",
                555L,
                dummyAttachment,
                "Receipt-TXN-12345.pdf"
        );

        EmailLog result = future.get();
        assertNotNull(result);
        assertEquals(EmailStatus.SIMULATED_SENT, result.getStatus());
        assertTrue(result.isHasAttachment());
        assertEquals("Receipt-TXN-12345.pdf", result.getAttachmentName());
    }

    /**
     * In-memory stub for EmailLogRepository to avoid external ByteBuddy dynamic agent attachment.
     */
    static class StubEmailLogRepository implements EmailLogRepository {
        final List<EmailLog> savedLogs = new ArrayList<>();
        private long idGen = 1L;

        @Override
        public <S extends EmailLog> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(idGen++);
            }
            if (!savedLogs.contains(entity)) {
                savedLogs.add(entity);
            }
            return entity;
        }

        @Override
        public boolean existsByEmailTypeAndReferenceIdAndStatusIn(EmailType emailType, Long referenceId, Collection<EmailStatus> statuses) {
            return savedLogs.stream().anyMatch(l ->
                    l.getEmailType() == emailType &&
                    Objects.equals(l.getReferenceId(), referenceId) &&
                    statuses.contains(l.getStatus())
            );
        }

        @Override public Optional<EmailLog> findById(Long id) {
            return savedLogs.stream().filter(l -> Objects.equals(l.getId(), id)).findFirst();
        }

        @Override public long countByStatus(EmailStatus status) {
            return savedLogs.stream().filter(l -> l.getStatus() == status).count();
        }

        @Override public long countByStatusIn(Collection<EmailStatus> statuses) {
            return savedLogs.stream().filter(l -> statuses.contains(l.getStatus())).count();
        }

        @Override public long countByCreatedAtAfter(LocalDateTime timestamp) { return 0; }
        @Override public Page<EmailLog> searchLogs(EmailType type, EmailStatus status, String search, Pageable pageable) { return Page.empty(); }
        @Override public void flush() {}
        @Override public <S extends EmailLog> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends EmailLog> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<EmailLog> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public EmailLog getOne(Long aLong) { return null; }
        @Override public EmailLog getById(Long aLong) { return null; }
        @Override public EmailLog getReferenceById(Long aLong) { return null; }
        @Override public <S extends EmailLog> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends EmailLog> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends EmailLog> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends EmailLog> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends EmailLog> long count(Example<S> example) { return 0; }
        @Override public <S extends EmailLog> boolean exists(Example<S> example) { return false; }
        @Override public <S extends EmailLog, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends EmailLog> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<EmailLog> findAll() { return savedLogs; }
        @Override public List<EmailLog> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return savedLogs.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(EmailLog entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends EmailLog> entities) {}
        @Override public void deleteAll() {}
        @Override public List<EmailLog> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<EmailLog> findAll(Pageable pageable) { return Page.empty(); }
    }
}
