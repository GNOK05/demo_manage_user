package com.example.demo.controller;

import com.example.demo.common.BaseResponse;
import com.example.demo.dto.request.UserGroupMappingDTO;
import com.example.demo.entity.Group;
import com.example.demo.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupsController {

    private final GroupService groupService;

    @GetMapping()
    public ResponseEntity<BaseResponse<List<Group>>> getAll(){
        return ResponseEntity.ok(new BaseResponse<>(groupService.getAll(),"Lay tat ca user thành công",null));

    }

    @PostMapping("/addUser")
    public ResponseEntity<BaseResponse<Void>> addUser(@Valid @RequestBody List<UserGroupMappingDTO> userGroupMappingDTOList){
        groupService.addUser(userGroupMappingDTOList);
        return ResponseEntity.ok(new BaseResponse<>(null,"Them nguoi dung thanh cong",null));

    }
    @DeleteMapping("/deleteUser")
    public ResponseEntity<BaseResponse<Void>> deleteUser(@Valid @RequestBody List<UserGroupMappingDTO> userGroupMappingDTOList){
        groupService.deleteUser(userGroupMappingDTOList);
        return ResponseEntity.ok(new BaseResponse<>(null,"Xoa nguoi dung thanh cong",null));

    }

}
