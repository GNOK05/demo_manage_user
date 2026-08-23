package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TaskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private String loginAdmin() throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"));
        var mvcResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String resp = mvcResult.getResponse().getContentAsString();
        var node = mapper.readTree(resp);
        return node.at("/data/token").asText();
    }

    @Test
    void createTaskMissingNameReturns400() throws Exception {
        String token = loginAdmin();

        // create minimal department
        String deptBody = mapper.writeValueAsString(Map.of("name", "TestDept", "code", "TD01"));
        var deptRes = mockMvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(deptBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var deptNode = mapper.readTree(deptRes.getResponse().getContentAsString());
        long deptId = deptNode.at("/data/id").asLong();

        // create project
        String projectBody = mapper.writeValueAsString(Map.of(
                "projectName", "Integration Project",
                "description", "desc",
                "departmentId", deptId,
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString()
        ));
        var projRes = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(projectBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var projNode = mapper.readTree(projRes.getResponse().getContentAsString());
        long projId = projNode.at("/data/id").asLong();

        // attempt to create task without taskName
        String taskBody = mapper.writeValueAsString(Map.of(
                "description", "a task without name",
                "projectId", projId,
                "deadline", LocalDate.now().plusDays(7).toString()
        ));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(taskBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createTaskSucceeds() throws Exception {
        String token = loginAdmin();

        // create minimal department
        String deptBody = mapper.writeValueAsString(Map.of("name", "TestDept2", "code", "TD02"));
        var deptRes = mockMvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(deptBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var deptNode = mapper.readTree(deptRes.getResponse().getContentAsString());
        long deptId = deptNode.at("/data/id").asLong();

        // create project
        String projectBody = mapper.writeValueAsString(Map.of(
                "projectName", "Integration Project 2",
                "description", "desc",
                "departmentId", deptId,
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(30).toString()
        ));
        var projRes = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(projectBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var projNode = mapper.readTree(projRes.getResponse().getContentAsString());
        long projId = projNode.at("/data/id").asLong();

        // create task with required fields
        String taskBody = mapper.writeValueAsString(Map.of(
                "taskName", "Integration Task",
                "description", "full",
                "projectId", projId,
                "deadline", LocalDate.now().plusDays(7).toString(),
                "status", "TODO"
        ));

        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(taskBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskName").value("Integration Task"));
    }
}
