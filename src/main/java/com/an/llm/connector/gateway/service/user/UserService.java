package com.an.llm.connector.gateway.service.user;

import com.an.llm.connector.gateway.dto.user.UserDto;
import com.an.llm.connector.gateway.entity.user.Group;
import com.an.llm.connector.gateway.entity.user.Role;
import com.an.llm.connector.gateway.entity.user.User;
import com.an.llm.connector.gateway.exception.AlreadyExistsException;
import com.an.llm.connector.gateway.exception.NotFoundException;
import com.an.llm.connector.gateway.exception.NullException;
import com.an.llm.connector.gateway.mapper.user.UserMapper;
import com.an.llm.connector.gateway.repository.user.GroupRepo;
import com.an.llm.connector.gateway.repository.user.RoleRepo;
import com.an.llm.connector.gateway.repository.user.UserRepo;
import com.an.llm.connector.gateway.util.AppUtils;
import com.an.llm.connector.gateway.util.EmailUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final GroupRepo groupRepo;
    private final RoleRepo roleRepo;
    private final EmailUtils emailUtils;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private final UserMapper userMapper;

    @Transactional
    public UserDto view(@NonNull String id) {
        if (id.contains("@")) {
            return userMapper.toDto(
                    userRepo.findByEmail(id).orElseThrow(()-> new NotFoundException("User does not exists."))
            );
        }
        return userMapper.toDto(
                userRepo.findByUsername(id).orElseThrow(() -> new NotFoundException("User does not exists."))
        );
    }

    @Transactional
    public List<UserDto> all(Boolean active) {
        if (active==null) {
            return userMapper.toDtoList(
                    userRepo.findAll()
            );
        }
        return userMapper.toDtoList(
                userRepo.findAllByActive(active)
        );
    }

    @Transactional
    public UserDto add(@NonNull UserDto dto){
        if (userRepo.existsByEmail(dto.getEmail())) throw new AlreadyExistsException("User with same email already exists.");

        User register = userMapper.toEntity(dto);

        register.setUsername(generateUsername(dto));

        //generate the Groups and Roles.
        Set<String> rolesMappedWithGroups = new HashSet<>();

        if (dto.getGroups() != null && !dto.getGroups().isEmpty()) {
            List<Group> groups = groupRepo.findByCodeIn(new ArrayList<>(dto.getGroups()));

            register.setGroups(new HashSet<>(groups));

            for (Group group : groups) {
                if (group.getRoles() != null && !group.getRoles().isEmpty()) {
                    rolesMappedWithGroups.addAll(
                            group.getRoles().stream().map(Role::getCode).toList()
                    );
                }
            }
        }

        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            List<Role> roles =  roleRepo.findByCodeIn(new ArrayList<>(dto.getRoles()));

            List<Role> finalRoles = new ArrayList<>();
            for (Role role : roles) {
                if (rolesMappedWithGroups.contains(role.getCode())) {
                    finalRoles.add(role);
                }
            }

            register.setRoles(new HashSet<>(finalRoles));
        }

        //for admin if password is given, do not auto-generate
        String password;
        if(dto.getGroups() != null && !dto.getGroups().isEmpty() && dto.getGroups().contains("admin") && dto.getPassword() != null && !dto.getPassword().isBlank()) {
            password = dto.getPassword();
        } else {
            password = AppUtils.generatePassword();
        }

        register.setPassword(bCryptPasswordEncoder.encode(password));

        User saved = userRepo.save(register);

        try {
            emailUtils.sendEmail(dto.getEmail(),"Account registered to LLM Connector Gateway!",String.format("We have add your account in LLM Connector Gateway. You can start using the AI services with the provided credentials.\n\nUsername: %s\nPassword: %s",register.getUsername(),password));
        } catch (Exception e){
            log.info("Error invoking email.",e);
        }

        return userMapper.toDto(saved);
    }

    @Transactional
    public UserDto update(@NonNull UserDto updateRequest) {
        User existingUser = userRepo.findByUsername(updateRequest.getUsername())
                .orElseThrow(()-> new NotFoundException("User not found."));

        if (updateRequest.getActive() != null) {
            existingUser.setActive(updateRequest.getActive());
        }
        if (updateRequest.getFirstName() != null && !updateRequest.getFirstName().isBlank()) {
            existingUser.setFirstName(updateRequest.getFirstName());
        }
        if (updateRequest.getLastName() != null && !updateRequest.getLastName().isBlank()) {
            existingUser.setLastName(updateRequest.getLastName());
        }
        //only indian number check
        if (updateRequest.getPhoneNumber() != null && !updateRequest.getPhoneNumber().isBlank() && updateRequest.getPhoneNumber().length()==10) {
            existingUser.setPhoneNumber(updateRequest.getPhoneNumber());
        }
        if (existingUser.getGroups() == null) {
            existingUser.setGroups(new HashSet<>());
        }
        if (existingUser.getRoles() == null) {
            existingUser.setRoles(new HashSet<>());
        }

        //update roles and groups (override)
        if (updateRequest.getGroups()==null || updateRequest.getGroups().isEmpty()) {
            existingUser.getGroups().clear();
            existingUser.getRoles().clear();
        } else {
            Set<String> rolesMappedWithGroups = new HashSet<>();
            //override groups.
            List<Group> groups = groupRepo.findByCodeIn(new ArrayList<>(updateRequest.getGroups()));

            existingUser.setGroups(new HashSet<>(groups));

            for (Group group : groups) {
                if (group.getRoles() != null && !group.getRoles().isEmpty()) {
                    rolesMappedWithGroups.addAll(
                            group.getRoles().stream().map(Role::getCode).toList()
                    );
                }
            }
            //override roles.
            List<Role> roles =  roleRepo.findByCodeIn(new ArrayList<>(updateRequest.getRoles()));

            List<Role> finalRoles = new ArrayList<>();
            for (Role role : roles) {
                if (rolesMappedWithGroups.contains(role.getCode())) {
                    finalRoles.add(role);
                }
            }

            existingUser.setRoles(new HashSet<>(finalRoles));
        }

        return userMapper.toDto(
                userRepo.save(existingUser)
        );
    }

    private String generateUsername(@NonNull UserDto dto) {
        String firstName = Objects.toString(dto.getFirstName(), "").toLowerCase();
        String lastName = Objects.toString(dto.getLastName(), "").toLowerCase();

        if (firstName.isBlank() && lastName.isBlank()) {
            throw new NullException("Unable to generate username. Name was blank.");
        }

        for (int i = 1; i <= firstName.length(); i++) {
            String username = firstName.substring(0, i) + lastName;

            if (!userRepo.existsByUsername(username)) {
                return username;
            }
        }

        String baseUsername = firstName + lastName;

        for (int postfix = 1; ; postfix++) {
            String username = baseUsername + postfix;

            if (!userRepo.existsByUsername(username)) {
                return username;
            }
        }
    }

}
