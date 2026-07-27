package com.example.demo.service;
import com.example.demo.dto.request.UserGroupMappingDTO;
import com.example.demo.entity.Group;

import java.util.List;

public interface GroupService {
    List<Group> getAll();
    void addUser( List<UserGroupMappingDTO> userGroupMappingDTOList);
    void deleteUser( List<UserGroupMappingDTO> userGroupMappingDTOList);
}
