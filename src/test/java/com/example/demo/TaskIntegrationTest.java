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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void readOnlyViewerCannotUpdateTaskStatus() throws Exception {
        String token = login("viewer_1", "viewer123");
        var tasksResponse = mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var taskData = mapper.readTree(tasksResponse.getResponse().getContentAsString()).at("/data");
        org.junit.jupiter.api.Assertions.assertTrue(taskData.isArray());
        org.junit.jupiter.api.Assertions.assertFalse(taskData.isEmpty());
        long taskId = taskData.get(0).get("id").asLong();

        mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"blockedReason\":\"Read-only test\"}"))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
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

        String requestBody = mapper.writeValueAsString(Map.of(
                "title", "Integration Request",
                "description", "Request for task integration test",
                "executingDepartmentId", deptId,
                "startDate", LocalDate.now().toString(),
                "deadline", LocalDate.now().plusDays(30).toString(),
                "priority", "URGENT"
        ));
        var requestResult = mockMvc.perform(post("/api/v1/projects/{projectId}/work-requests", projId)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projId))
                .andReturn();
        long requestId = mapper.readTree(requestResult.getResponse().getContentAsString()).at("/data/id").asLong();

        // create task with required fields
        String taskBody = mapper.writeValueAsString(Map.of(
                "taskName", "Integration Task",
                "description", "full",
                "projectId", projId,
                "workRequestId", requestId,
                "startDate", LocalDate.now().toString(),
                "deadline", LocalDate.now().plusDays(7).toString(),
                "status", "TODO",
                "priority", "URGENT",
                "estimatedHours", 12,
                "acceptanceCriteria", "All automated tests pass"
        ));

        var taskResult = mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(taskBody)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskName").value("Integration Task"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.status").value("BACKLOG"))
                .andExpect(jsonPath("$.data.estimatedHours").value(12))
                .andExpect(jsonPath("$.data.acceptanceCriteria").value("All automated tests pass"))
                .andReturn();

        long taskId = mapper.readTree(taskResult.getResponse().getContentAsString()).at("/data/id").asLong();
        mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"blockedReason\":\"Waiting for API access\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.blockedReason").value("Waiting for API access"));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("keyword", "Integration Task")
                        .param("priority", "URGENT")
                        .param("status", "BLOCKED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(taskId));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("keyword", "Integration Task")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

        @Test
        void taskSearchSupportsContainsRoleStatusAndSortParameters() throws Exception {
                String token = loginAdmin();

                mockMvc.perform(get("/api/v1/tasks")
                                                .param("keyword", "engineer")
                                                .param("role", "MEMBER")
                                                .param("status", "IN_PROGRESS")
                                                .param("sort", "DEADLINE_ASC")
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                                .andExpect(jsonPath("$.data[0].assignedToName").exists());
        }

        @Test
        void taskCannotSkipWorkflowStages() throws Exception {
                String body = mapper.writeValueAsString(Map.of("username", "lead_1", "password", "lead123"));
                var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk()).andReturn();
                String token = mapper.readTree(loginResult.getResponse().getContentAsString()).at("/data/token").asText();

                var taskResult = mockMvc.perform(get("/api/v1/tasks/my").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andReturn();
                long taskId = mapper.readTree(taskResult.getResponse().getContentAsString()).at("/data/0/id").asLong();
                String statusBody = "{\"status\":\"DONE\"}";

                mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(statusBody))
                        .andExpect(status().isBadRequest());

                mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(statusBody))
                        .andExpect(status().isBadRequest());
        }
}
