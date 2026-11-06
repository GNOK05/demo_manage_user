package com.example.demo;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.ProjectStatus;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WorkRequestWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired PasswordEncoder encoder;

    @Test
    void portfolioCreatesAndAssignedLeadAdvancesButCannotAccept() throws Exception {
        User portfolio = new User();
        portfolio.setUsername("portfolio_workflow_test");
        portfolio.setPassword(encoder.encode("portfolio123"));
        portfolio.setFullName("Portfolio Workflow Test");
        portfolio.setEmail("portfolio.workflow@test.local");
        portfolio.setRole(Role.PORTFOLIO_MANAGER);
        users.save(portfolio);
        User lead = users.findByUsername("lead_1").orElseThrow();
        var project = projects.findByDepartmentId(lead.getDepartment().getId()).stream()
                .filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).findFirst().orElseThrow();
        String body = json.writeValueAsString(Map.of(
                "title", "Workflow permissions", "executingDepartmentId", lead.getDepartment().getId(),
                "coordinatorId", lead.getId(), "startDate", project.getStartDate().toString(),
                "deadline", project.getEndDate().toString(), "priority", "NORMAL"));
        String portfolioToken = login("portfolio_workflow_test", "portfolio123");
        var created = mvc.perform(post("/api/v1/projects/{id}/work-requests", project.getId())
                .header("Authorization", "Bearer " + portfolioToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(post("/api/v1/projects/{id}/work-requests", project.getId())
                .header("Authorization", "Bearer " + login("hr", "hr123"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/work-requests/{id}/status", id)
                .header("Authorization", "Bearer " + portfolioToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANALYSIS\"}"))
                .andExpect(status().isOk());
        String leadToken = login("lead_1", "lead123");
        mvc.perform(patch("/api/v1/work-requests/{id}/status", id)
                .header("Authorization", "Bearer " + leadToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk());
        User developer = users.findByUsername("dev_1_1").orElseThrow();
        String taskBody = json.writeValueAsString(Map.of(
                "taskName", "Implement workflow", "workRequestId", id,
                "projectId", project.getId(), "assignedToId", developer.getId(),
                "startDate", project.getStartDate().toString(),
                "deadline", project.getEndDate().toString(), "taskType", "DEVELOPMENT"));
        var taskResult = mvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + leadToken)
                .contentType(MediaType.APPLICATION_JSON).content(taskBody))
                .andExpect(status().isOk()).andReturn();
        long taskId = json.readTree(taskResult.getResponse().getContentAsString()).at("/data/id").asLong();
        String bugBody = json.writeValueAsString(Map.of(
                "taskName", "Regression bug", "workRequestId", id,
                "projectId", project.getId(), "relatedTaskId", taskId,
                "startDate", project.getStartDate().toString(),
                "deadline", project.getEndDate().toString(), "taskType", "BUG"));
        mvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + login("test_1_1", "qa123"))
                .contentType(MediaType.APPLICATION_JSON).content(bugBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relatedTaskId").value(taskId));
        for (String stage : new String[]{"DEVELOPMENT", "TESTING"}) {
            mvc.perform(patch("/api/v1/work-requests/{id}/status", id)
                    .header("Authorization", "Bearer " + leadToken)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + stage + "\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(patch("/api/v1/work-requests/{id}/status", id)
                .header("Authorization", "Bearer " + leadToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING_ACCEPTANCE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/work-requests/{id}/status", id)
                .header("Authorization", "Bearer " + leadToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/token").asText();
    }
}
