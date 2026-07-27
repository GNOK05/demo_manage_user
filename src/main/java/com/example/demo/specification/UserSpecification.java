package com.example.demo.specification;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import org.springframework.data.jpa.domain.Specification;

public class UserSpecification {
    private UserSpecification() {
    }
    public static Specification<User> hasFirstNameLike(String firstName){
        return (root, query, builder) ->
             builder.like(builder.lower(root.get("firstName")),"%"+firstName.toLowerCase()+ "%");

    }
    public static Specification<User> hasLastNameLike(String lastName){
        return (root, query, builder) ->
             builder.like(builder.lower(root.get("lastName")),"%"+lastName.toLowerCase()+ "%");
    }
    public static Specification<User> hasAddressLike(String address){
        return (root, query, builder) ->
             builder.like(builder.lower(root.get("address")),"%"+address+ "%");
    }
    public static Specification<User> hasRoleLike(Role role){
        return (root, query, builder) ->
                builder.equal(root.get("role"), role);
    }


}
