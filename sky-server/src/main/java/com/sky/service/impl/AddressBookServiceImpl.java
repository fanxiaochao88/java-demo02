package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Override
    public void insert(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    @Override
    public List<AddressBook> list() {
        return addressBookMapper.list(BaseContext.getCurrentId());
    }

    @Override
    public AddressBook getDefault() {
        return addressBookMapper.findDefault(BaseContext.getCurrentId());
    }

    @Transactional
    @Override
    public void setDefault(AddressBook addressBook) {
        // 1. 清理所有地址的默认状态
        addressBookMapper.updateIsDefaultByUserId(BaseContext.getCurrentId());
        // 2. 设置当前地址为默认地址
        addressBookMapper.setDefault(addressBook.getId());
    }

    @Override
    public void delete(Long id) {
        addressBookMapper.delete(id, BaseContext.getCurrentId());
    }

    @Override
    public AddressBook getById(Long id) {
        return addressBookMapper.getById(id);
    }

    @Override
    public void update(AddressBook addressBook) {
        addressBookMapper.update(addressBook);
    }
}
