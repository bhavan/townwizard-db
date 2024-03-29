package com.townwizard.db.services;

import org.jasypt.util.password.PasswordEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.townwizard.db.dao.UserDao;
import com.townwizard.db.model.LoginRequest;
import com.townwizard.db.model.User;
import com.townwizard.db.model.User.LoginType;

/**
 * UserService interface implementation
 */
@Component("userService")
@Transactional
public class UserServiceImpl implements UserService {    
    
    @Autowired
    private UserDao userDao;
    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Override
    public User getById(Long id) {
        return userDao.getById(User.class, id);
    }
    
    @Override
    public User getByEmailAndLoginType(String email, LoginType loginType) {
        if(email == null) {
            throw new ServiceException("Can only get user by email when email is not null");
        }
        return userDao.getByEmailAndLoginType(email, loginType);
    }
    
    @Override
    public User getByExternalIdAndLoginType(String externalId, LoginType loginType) {
        if(externalId == null) {
            throw new ServiceException("Can only get user by external id when it is not null");
        }
        return userDao.getByExternalIdAndLoginType(externalId, loginType);
    }
    
    @Override
    public User login(String email, String password) {
        if(email == null) {
            throw new ServiceException("Can only login a user when email is not null");
        }
        
        if(password == null) {
            throw new ServiceException("Can only login a user when password is not null");
        }        
        
        User user = userDao.getByEmailAndLoginType(email, LoginType.TOWNWIZARD);
        if(user != null) {
            if(passwordEncryptor.checkPassword(password, user.getPassword())) {
                return user;
            }
        }
        return null;
    }
    
    @Override
    public Long create(User user) {
        encryptPassword(user);
        userDao.create(user);
        return user.getId();
    }
    
    @Override
    public void update(User user) {
        encryptPassword(user);
        userDao.update(user);        
    }
    
    @Override
    public void createLoginRequest(LoginRequest loginRequest) {
        userDao.createLoginRequest(loginRequest);
    }
    
    @Override
    public LoginRequest getLoginRequest(String uuid) {
        return userDao.getLoginRequest(uuid);
    }
    
    private void encryptPassword(User user) {
        String plainPassword = user.getPassword();
        if(plainPassword != null) {
            user.setPassword(passwordEncryptor.encryptPassword(plainPassword));
        }
    }
}