package com.example.demo.service.impl;
import com.example.demo.entity.Group;
import com.example.demo.exception.BussinessException;
import com.example.demo.specification.UserSpecification;
import com.example.demo.dto.request.CreateUserDTO;
import com.example.demo.dto.request.UpdateUserDTO;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFiller;
import com.example.demo.repository.UserGroupMappingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRespository;
    private final ModelMapper modelMapper;
    private final UserGroupMappingRepository userGroupMappingRepository;
    private final PasswordEncoder passwordEncoder;



    @Override
    @Transactional
    public User create( CreateUserDTO request) {
        User user = modelMapper.map(request,User.class);
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        return userRespository.save(user);
    }
    @Transactional
    @Override
    public User updateUser(Integer id, UpdateUserDTO update){
        User user = userRespository.findById(id)
                .orElseThrow(() ->  new BussinessException("Không tìm thấy User với ID: " + id));
        modelMapper.map(update,user);
        if (update.getPassword()!= null) {
            String encodedPassword = passwordEncoder.encode(update.getPassword());
            user.setPassword(encodedPassword);
        }

        return userRespository.save(user);
    }
    @Override
    public List<User> search(UserFiller userFiller){
        String firstName= userFiller.getFirstName();
        String lastName = userFiller.getLastName();
        String address = userFiller.getAddress();
        Role role = userFiller.getRole();
        Specification<User> spec = (root, query, builder) -> builder.conjunction();
        if (firstName != null && !firstName.isBlank()){
            spec=spec.and(UserSpecification.hasFirstNameLike(firstName));
        }
        if (lastName != null && !lastName.isBlank()){
            spec=spec.and(UserSpecification.hasLastNameLike(lastName));
        }
        if (address != null && !address.isBlank()){
            spec=spec.and(UserSpecification.hasAddressLike(address));
        }
        if (role != null ){
            spec=spec.and(UserSpecification.hasRoleLike(role));
        }
        return userRespository.findAll(spec);

    }
    @Transactional
    public void deleteByID(Integer id){
        if (id==null) {
            throw new BussinessException(("ID không được để trống"));
        }
        if (!userRespository.existsById(id)){
            throw new BussinessException("không tìm thấy User với Id =" + id + "để xoá");
        }
        userRespository.deleteById(id);
        userGroupMappingRepository.deleteByUserId(id);
    }

    @Override
    public List<User> findByBirthdayBetween(LocalDate fromDate, LocalDate toDate) {
        return userRespository.findByBirthdayBetweenSQL(fromDate,toDate);
    }

    @Override
    public Page<User> getAllUsers(Pageable pageable) {
        return userRespository.findAll(pageable);
    }

    @Override
    public List<Group> findGroupByUserId(Integer userId) {
        return userRespository.findGroupdByUserId(userId);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRespository.findByUsername(username);
        if (user==null){
            throw new UsernameNotFoundException("Username not found");
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );

    }
}
