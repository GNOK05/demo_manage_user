package com.example.demo.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public final class DepartmentDto {
    private DepartmentDto() {}
    public record SaveRequest(@NotBlank String name, @NotBlank String code, @Size(max = 2000) String description, Long managerId) {}
    public record Response(Long id, String name, String code, String description, Long managerId, String managerName) {}
}
