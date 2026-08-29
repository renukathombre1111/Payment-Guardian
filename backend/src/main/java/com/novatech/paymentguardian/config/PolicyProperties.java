package com.novatech.paymentguardian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "guardian.policy")
public class PolicyProperties {

    private BigDecimal maxPaymentWithoutApproval = new BigDecimal("500000");
    private int bankCoolingHours = 24;
    private BigDecimal newVendorMaxPayment = new BigDecimal("200000");
    private BigDecimal minCashBuffer = new BigDecimal("2500000");
    private boolean invoiceRequired = true;

    public BigDecimal getMaxPaymentWithoutApproval() {
        return maxPaymentWithoutApproval;
    }

    public void setMaxPaymentWithoutApproval(BigDecimal maxPaymentWithoutApproval) {
        this.maxPaymentWithoutApproval = maxPaymentWithoutApproval;
    }

    public int getBankCoolingHours() {
        return bankCoolingHours;
    }

    public void setBankCoolingHours(int bankCoolingHours) {
        this.bankCoolingHours = bankCoolingHours;
    }

    public BigDecimal getNewVendorMaxPayment() {
        return newVendorMaxPayment;
    }

    public void setNewVendorMaxPayment(BigDecimal newVendorMaxPayment) {
        this.newVendorMaxPayment = newVendorMaxPayment;
    }

    public BigDecimal getMinCashBuffer() {
        return minCashBuffer;
    }

    public void setMinCashBuffer(BigDecimal minCashBuffer) {
        this.minCashBuffer = minCashBuffer;
    }

    public boolean isInvoiceRequired() {
        return invoiceRequired;
    }

    public void setInvoiceRequired(boolean invoiceRequired) {
        this.invoiceRequired = invoiceRequired;
    }
}
