package com.example.demo.controller;

import com.example.demo.common.BaseResponse;
import com.example.demo.dto.request.CreateUserDTO;
import com.example.demo.dto.request.UpdateUserDTO;
import com.example.demo.entity.Group;
import com.example.demo.entity.User;
import com.example.demo.entity.UserFiller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.service.UserService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UsersController {
    private final UserService userService;

    @GetMapping()
    public ResponseEntity<BaseResponse<Page<User>>> getAllUsers(Pageable pageable){
        return ResponseEntity.ok(new BaseResponse<>(userService.getAllUsers(pageable),"Lay danh sach user thành công",null));

    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BaseResponse<User>> create( @RequestBody @Valid CreateUserDTO request){
        return ResponseEntity.ok(new BaseResponse<>(userService.create(request),"Tạo user thành công",null));

    }
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<User>> updateUser(@PathVariable Integer id,@RequestBody @Valid UpdateUserDTO update){
        return ResponseEntity.ok(new BaseResponse<>(userService.updateUser(id,update),"Cập nhật user thành công",null));
    }

    @GetMapping("/search")
    public ResponseEntity<BaseResponse< List<User>>> search(@Valid UserFiller userFiller) {
        return ResponseEntity.ok(new BaseResponse<>(userService.search(userFiller),"Tìm kiếm thành công",null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteById(@PathVariable Integer id){
         userService.deleteByID(id);
    }

    @GetMapping("/findByBirthdayBetween")
    public ResponseEntity<BaseResponse<List<User>>> findByBirthdayBetween(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate, @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd")  LocalDate toDate){
        return ResponseEntity.ok(new BaseResponse<>(userService.findByBirthdayBetween(fromDate,toDate),"Tìm kiếm ngày sinh giữa 2 mốc thời gian thành công",null));

    }
    @GetMapping("/findGroupByUserId/{userId}")
    public ResponseEntity<BaseResponse<List<Group>>> findGroupByUserId(@PathVariable Integer userId){
        return ResponseEntity.ok(new BaseResponse<>(userService.findGroupByUserId(userId),"Tìm kiếm groups by userId thanh cong ",null));

    }
}
