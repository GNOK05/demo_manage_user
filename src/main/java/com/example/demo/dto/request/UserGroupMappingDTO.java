package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupMappingDTO {

    @NotNull(message = "groupId không được để trống")
    private Integer groupId;

    @NotNull(message = "userId không được để trống")
    private Integer userId;
}
