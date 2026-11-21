package com.banking.productservice.application.command;

import com.banking.productservice.domain.model.Eligibility;
import com.banking.productservice.domain.model.ProductFamily;
import com.banking.productservice.domain.model.ProductTerms;

/** New catalog entry. The aggregate validates the values when the handler creates it. */
public record CreateProductCommand(
    String code, String name, ProductFamily family, Eligibility eligibility, ProductTerms terms) {}
