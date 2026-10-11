package com.moveinsync.settlement.invoice.repository;

import com.moveinsync.settlement.invoice.entity.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {
    Optional<VendorInvoice> findByVendorIdAndInvoiceRef(Long vendorId, String invoiceRef);
    List<VendorInvoice> findByVendorId(Long vendorId);
}
