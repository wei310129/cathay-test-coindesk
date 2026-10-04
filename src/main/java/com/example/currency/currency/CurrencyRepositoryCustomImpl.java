package com.example.currency.currency;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

public class CurrencyRepositoryCustomImpl implements CurrencyRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void insert(Currency currency) {
        entityManager.persist(currency);
        entityManager.flush();
    }
}
