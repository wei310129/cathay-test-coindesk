package com.example.currency.currency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String>, CurrencyRepositoryCustom {
}
