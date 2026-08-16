package com.example.demo.dto;
import jakarta.validation.constraints.NotBlank;
public final class DepartmentDto {
    private DepartmentDto() {}
    public record SaveRequest(@NotBlank String name, @NotBlank String code, String description, Long managerId) {}
    public record Response(Long id, String name, String code, String description, Long managerId, String managerName) {}
}
