package com.sky.service;

import com.sky.entity.AddressBook;

import java.util.List;

public interface AddressBookService {
    void insert(AddressBook addressBook);

    List<AddressBook> list();

    AddressBook getDefault();

    void setDefault(AddressBook addressBook);

    void delete(Long id);

    AddressBook getById(Long id);

    void update(AddressBook addressBook);
}
