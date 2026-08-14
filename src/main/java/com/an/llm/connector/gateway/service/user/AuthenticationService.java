package com.an.llm.connector.gateway.service.user;

import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.mapper.user.UserMapper;
import com.an.llm.connector.gateway.model.auth.LoginRequest;
import com.an.llm.connector.gateway.repository.user.UserRepo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepo userRepo;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private final UserMapper userMapper;

    public boolean authenticate(@NonNull LoginRequest request) {
        if (request.getPassword()==null || request.getUserid() == null) throw new NullException("Username/password missing.");

        String password = null;

        if (request.getUserid().contains("@")) {
            password = userRepo.findPasswordByUsernameOrEmail(null,request.getUserid())
                    .orElseThrow(()-> new NotFoundException("User does not exists."));
        } else {
            password = userRepo.findPasswordByUsernameOrEmail(request.getUserid(),null)
                    .orElseThrow(()-> new NotFoundException("User does not exists."));
        }

        return bCryptPasswordEncoder.matches(request.getPassword() ,password);
    }
}
