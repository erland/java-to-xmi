package com.acme.service;

import com.acme.annotations.Service;
import com.acme.model.Customer;

@Service(name = "customer", version = 2)
public class CustomerService {
    public Customer findById(String id) {
        return new Customer(id, "Alice");
    }
}
