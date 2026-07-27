package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table(name="`user_group_mapping`")
@Entity
@Getter
@Setter
public class UserGroupMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;
    @Column(name = "group_id")
    private Integer groupId;
}
