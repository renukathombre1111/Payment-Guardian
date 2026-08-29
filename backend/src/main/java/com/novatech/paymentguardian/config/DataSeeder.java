package com.novatech.paymentguardian.config;

import com.novatech.paymentguardian.domain.*;
import com.novatech.paymentguardian.dto.ApiDtos.EvaluateRequest;
import com.novatech.paymentguardian.repo.*;
import com.novatech.paymentguardian.service.InvestigationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final VendorRepository vendors;
    private final VendorBankAccountRepository accounts;
    private final VendorTransactionRepository transactions;
    private final InvoiceRepository invoices;
    private final BankTransactionRepository bankTx;
    private final CashPositionRepository cashRepo;
    private final ObligationRepository obligations;
    private final PaymentCaseRepository cases;
    private final InvestigationService investigation;

    public DataSeeder(
            VendorRepository vendors,
            VendorBankAccountRepository accounts,
            VendorTransactionRepository transactions,
            InvoiceRepository invoices,
            BankTransactionRepository bankTx,
            CashPositionRepository cashRepo,
            ObligationRepository obligations,
            PaymentCaseRepository cases,
            InvestigationService investigation
    ) {
        this.vendors = vendors;
        this.accounts = accounts;
        this.transactions = transactions;
        this.invoices = invoices;
        this.bankTx = bankTx;
        this.cashRepo = cashRepo;
        this.obligations = obligations;
        this.cases = cases;
        this.investigation = investigation;
    }

    @Override
    public void run(String... args) {
        if (vendors.count() > 0) {
            return;
        }

        Instant now = Instant.parse("2026-08-27T16:00:00Z");
        LocalDate today = LocalDate.of(2026, 8, 27);

        seedSharedFinancials(now, today);
        Vendor abc = seedAbcReview(now, today);
        Vendor office = seedOfficeKart(now, today);
        seedCompromisedBlock(now, today);

        investigation.evaluate(new EvaluateRequest(abc.getId(), new BigDecimal("1850000"), today, false));
        investigation.evaluate(new EvaluateRequest(office.getId(), new BigDecimal("24000"), today, false));
    }

    private void seedSharedFinancials(Instant now, LocalDate today) {
        cashRepo.save(CashPosition.builder()
                .date(today)
                .openingBalance(new BigDecimal("11800000"))
                .inflow(new BigDecimal("600000"))
                .outflow(new BigDecimal("400000"))
                .closingBalance(new BigDecimal("12000000"))
                .build());

        obligations.save(Obligation.builder().name("Payroll").amount(new BigDecimal("4200000"))
                .dueDate(LocalDate.of(2026, 9, 3)).type("PAYROLL").priority("HIGH").build());
        obligations.save(Obligation.builder().name("GST").amount(new BigDecimal("820000"))
                .dueDate(LocalDate.of(2026, 9, 5)).type("TAX").priority("HIGH").build());
        obligations.save(Obligation.builder().name("Other operating").amount(new BigDecimal("1200000"))
                .dueDate(LocalDate.of(2026, 9, 4)).type("OPEX").priority("MEDIUM").build());
        obligations.save(Obligation.builder().name("Customer receivable — Apex Retail").amount(new BigDecimal("3500000"))
                .dueDate(today.plusDays(1)).type("RECEIVABLE").priority("HIGH").build());

        bankTx.save(BankTransaction.builder()
                .amount(new BigDecimal("3500000"))
                .description("Expected inflow Apex Retail")
                .reference("AR-8821")
                .timestamp(now.plus(24, ChronoUnit.HOURS))
                .status("PENDING")
                .build());
    }

    /** Demo: ABC Technologies — REVIEW (invoice OK, bank changed, amount spike). */
    private Vendor seedAbcReview(Instant now, LocalDate today) {
        Vendor abc = vendors.save(Vendor.builder()
                .name("ABC Technologies")
                .email("ap@abctech.example")
                .category("IT Services")
                .riskScore(42)
                .createdAt(now.minus(400, ChronoUnit.DAYS))
                .build());

        accounts.save(VendorBankAccount.builder()
                .vendorId(abc.getId())
                .accountNumber("XXXX4921")
                .ifsc("HDFC0001111")
                .createdAt(now.minus(400, ChronoUnit.DAYS))
                .active(false)
                .build());
        accounts.save(VendorBankAccount.builder()
                .vendorId(abc.getId())
                .accountNumber("XXXX8837")
                .ifsc("YESB0002222")
                .createdAt(now.minus(19, ChronoUnit.HOURS))
                .active(true)
                .build());

        List<BigDecimal> hist = List.of(
                new BigDecimal("380000"), new BigDecimal("410000"), new BigDecimal("430000"),
                new BigDecimal("390000"), new BigDecimal("420000"));
        int i = 0;
        for (BigDecimal amt : hist) {
            transactions.save(VendorTransaction.builder()
                    .vendorId(abc.getId())
                    .amount(amt)
                    .type("PAYMENT")
                    .status("PAID")
                    .timestamp(now.minus(90L - i * 15L, ChronoUnit.DAYS))
                    .reference("NEFT-XXXX4921-" + (i + 1))
                    .build());
            i++;
        }

        invoices.save(Invoice.builder()
                .vendorId(abc.getId())
                .invoiceNumber("INV-29382")
                .amount(new BigDecimal("1850000"))
                .dueDate(today)
                .status("OPEN")
                .build());

        obligations.save(Obligation.builder().name("Vendor ABC").amount(new BigDecimal("1850000"))
                .dueDate(today).type("VENDOR").priority("HIGH").vendorId(abc.getId()).build());

        return abc;
    }

    /** Demo: OfficeKart — normal small payment → APPROVE. */
    private Vendor seedOfficeKart(Instant now, LocalDate today) {
        Vendor office = vendors.save(Vendor.builder()
                .name("OfficeKart Supplies")
                .email("billing@officekart.example")
                .category("Office")
                .riskScore(8)
                .createdAt(now.minus(200, ChronoUnit.DAYS))
                .build());

        accounts.save(VendorBankAccount.builder()
                .vendorId(office.getId())
                .accountNumber("XXXX2201")
                .ifsc("ICIC0003333")
                .createdAt(now.minus(180, ChronoUnit.DAYS))
                .active(true)
                .build());

        for (int j = 0; j < 5; j++) {
            transactions.save(VendorTransaction.builder()
                    .vendorId(office.getId())
                    .amount(new BigDecimal("22000").add(BigDecimal.valueOf(j * 500)))
                    .type("PAYMENT")
                    .status("PAID")
                    .timestamp(now.minus(60L - j * 12L, ChronoUnit.DAYS))
                    .reference("NEFT-XXXX2201-" + (j + 1))
                    .build());
        }

        invoices.save(Invoice.builder()
                .vendorId(office.getId())
                .invoiceNumber("INV-11002")
                .amount(new BigDecimal("24000"))
                .dueDate(today)
                .status("OPEN")
                .build());

        return office;
    }

    /** Demo: compromised vendor — BLOCK (no invoice, new bank, huge amount, new vendor). */
    private void seedCompromisedBlock(Instant now, LocalDate today) {
        Vendor shadow = vendors.save(Vendor.builder()
                .name("Shadow IT Consulting")
                .email("pay@shadow-it-fake.example")
                .category("IT Services")
                .riskScore(85)
                .createdAt(now.minus(14, ChronoUnit.DAYS))
                .build());

        accounts.save(VendorBankAccount.builder()
                .vendorId(shadow.getId())
                .accountNumber("XXXX9999")
                .ifsc("SBIN0009999")
                .createdAt(now.minus(6, ChronoUnit.HOURS))
                .active(true)
                .build());

        // No matching invoice — triggers INVOICE_REQUIRED policy BLOCK
        investigation.evaluate(new EvaluateRequest(
                shadow.getId(), new BigDecimal("2500000"), today, false));
    }
}
