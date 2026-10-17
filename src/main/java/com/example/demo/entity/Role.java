package com.example.demo.entity;

public enum Role {
    ADMIN,
    HR,
    PROJECT_MANAGER,
    TEAM_LEAD,
    MEMBER,
    QA,
    VIEWER;

    public boolean canManageUsers() {
        return this == ADMIN || this == HR;
    }

    public boolean canManageProjects() {
        return this == ADMIN || this == PROJECT_MANAGER;
    }

    public boolean canManageTasks() {
        return this == ADMIN || this == PROJECT_MANAGER || this == TEAM_LEAD;
    }

    public boolean canReviewPeopleInDepartment() {
        return this == HR || this == PROJECT_MANAGER || this == TEAM_LEAD;
    }

    public boolean isReadOnly() {
        return this == VIEWER;
    }
}
