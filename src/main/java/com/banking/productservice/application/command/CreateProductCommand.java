package com.banking.productservice.application.command;

import com.banking.productservice.domain.model.enums.ProductFamily;
import com.banking.productservice.domain.model.vo.Eligibility;
import com.banking.productservice.domain.model.vo.ProductTerms;

/** New catalog entry. The aggregate validates the values when the handler creates it. */
public record CreateProductCommand(
    String code, String name, ProductFamily family, Eligibility eligibility, ProductTerms terms) {}
