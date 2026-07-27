package com.example.demo.dto.request;

import com.example.demo.entity.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateUserDTO {

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    private LocalDate birthday;

    private String address;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;
}
