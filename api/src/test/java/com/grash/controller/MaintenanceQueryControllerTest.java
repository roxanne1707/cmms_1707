package com.grash.controller;

import com.grash.controller.query.MaintenanceQueryController;
import com.grash.dto.query.*;
import com.grash.exception.CustomException;
import com.grash.service.UserService;
import com.grash.service.query.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=MaintenanceQueryController.class,properties="enhancement.query.enabled=true")
@AutoConfigureMockMvc(addFilters=false)
class MaintenanceQueryControllerTest extends AbstractControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AssetWorkOrderOverviewService overviewService;
    @MockitoBean MaintenanceLedgerService ledgerService;
    @MockitoBean UserService userService;
    private static final String BODY="{\"start\":\"2026-01-01T00:00:00Z\",\"end\":\"2026-02-01T00:00:00Z\"}";
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private UsernamePasswordAuthenticationToken auth(String role){
        var auth=new UsernamePasswordAuthenticationToken("test",null,List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);return auth;
    }
    @Test void clientReceivesLedgerAndOriginalAuthenticationIsPassed()throws Exception{
        var auth=auth("ROLE_CLIENT");when(ledgerService.search(eq(auth),eq(11L),any())).thenReturn(new LedgerPage(List.of(),0,0,25));
        mvc.perform(post("/query/maintenance-ledger").principal(auth).param("companyId","11")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(25));
    }
    @Test void wrongRoleNeverCallsQuery()throws Exception{
        var auth=auth("ROLE_SUPER_ADMIN");
        mvc.perform(post("/query/maintenance-ledger").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ledgerService);
    }
    @Test void freshPolicyDenialRemainsForbidden()throws Exception{
        var auth=auth("ROLE_CLIENT");when(overviewService.overview(eq(auth),isNull(),eq(101L),any()))
                .thenThrow(new CustomException("Access denied",HttpStatus.FORBIDDEN));
        mvc.perform(post("/query/assets/101/work-order-overview").principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
    @Test void invalidEnumIsBadRequest()throws Exception{
        var auth=auth("ROLE_CLIENT");
        mvc.perform(post("/query/maintenance-ledger").principal(auth).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.substring(0,BODY.length()-1)+",\"status\":\"COMPLETE;DROP TABLE work_order\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ledgerService);
    }
}
