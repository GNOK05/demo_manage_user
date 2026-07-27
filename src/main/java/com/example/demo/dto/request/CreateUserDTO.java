package com.example.demo.dto.request;

import com.example.demo.entity.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Getter
@Setter

public class CreateUserDTO {
    @NotBlank(message = "FirstName không được để trống")
    @Size(min =3,max=255)
    private String firstName;

    @NotBlank(message = "LatName không được để trống")
    @Size(min =3,max=255)
    private String lastName;

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    @NotNull(message = "Birthday không được để trống")
    private LocalDate birthday;

    @NotBlank(message = "Address không được để trống")
    private String address;

    @NotBlank(message = "Password không được để trống")
    private String password;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Role role;

    @NotBlank
    private String username;
}
