package com.example.demo.service.impl;

import com.example.demo.exception.BussinessException;
import com.example.demo.dto.request.UserGroupMappingDTO;
import com.example.demo.entity.Group;
import com.example.demo.entity.UserGroupMapping;
import com.example.demo.repository.GroupRepository;
import com.example.demo.repository.UserGroupMappingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private final GroupRepository groupRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final UserGroupMappingRepository userGroupMappingRepository;

    @Override
    public List<Group> getAll(){
        return groupRepository.findAll();
    }


    @Override
    @Transactional
    public void addUser(List<UserGroupMappingDTO> userGroupMappingDTOList) {

        List<UserGroupMapping> mappingsToSave = new ArrayList<>();

        for (UserGroupMappingDTO dto : userGroupMappingDTOList) {

            if (!groupRepository.existsById(dto.getGroupId())) {
                throw new BussinessException("Nhóm với ID " + dto.getGroupId() + " chưa tồn tại");
            }
            if (!userRepository.existsById(dto.getUserId())) {
                throw new BussinessException("User với ID " + dto.getUserId() + " chưa tồn tại");
            }
            if (userGroupMappingRepository.existsByGroupIdAndUserId(dto.getGroupId(), dto.getUserId())) {
                throw new BussinessException("User " + dto.getUserId() + " đã có trong nhóm " + dto.getGroupId());
            }

            UserGroupMapping mapping = new UserGroupMapping();
            mapping.setGroupId(dto.getGroupId());
            mapping.setUserId(dto.getUserId());

            mappingsToSave.add(mapping);
        }

        userGroupMappingRepository.saveAll(mappingsToSave);
    }

    @Override
    @Transactional
    public void deleteUser(List<UserGroupMappingDTO> dtoList) {
        List<UserGroupMapping> mappingsToDelete = new ArrayList<>();

        for (UserGroupMappingDTO dto : dtoList) {
            UserGroupMapping mapping = userGroupMappingRepository.findByGroupIdAndUserId(dto.getGroupId(), dto.getUserId())
                    .orElseThrow(() -> new BussinessException("User " + dto.getUserId() + " không ở trong nhóm " + dto.getGroupId()));

            mappingsToDelete.add(mapping);
        }
        userGroupMappingRepository.deleteAllInBatch(mappingsToDelete);
    }
}
