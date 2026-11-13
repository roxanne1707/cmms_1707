package com.grash.controller;

import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.security.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSessionControllerTest {
    final SessionRegistry sessions=mock(SessionRegistry.class);
    final JwtTokenProvider jwt=mock(JwtTokenProvider.class);
    final AuthSessionController controller=new AuthSessionController(sessions,jwt);
    final HttpServletRequest request=mock(HttpServletRequest.class);
    User user(){User u=new User();u.setId(3L);return u;}
    UUID authenticated(){UUID id=UUID.randomUUID();when(request.getHeader("Authorization")).thenReturn("Bearer signed");when(jwt.resolveToken(request)).thenReturn("signed");when(jwt.getSessionId("signed")).thenReturn(id);return id;}
    @Test void currentLogoutUsesVerifiedJwtSidAndCurrentActor(){UUID id=authenticated();assertTrue(controller.logoutCurrent(user(),request).isSuccess());verify(sessions).revoke(id,3L);verify(sessions,never()).revokeAll(anyLong());}
    @Test void oldTokenNeverSilentlyLogsOutAllDevices(){when(request.getHeader("Authorization")).thenReturn("Bearer legacy");when(jwt.resolveToken(request)).thenReturn("legacy");CustomException ex=assertThrows(CustomException.class,()->controller.logoutCurrent(user(),request));assertEquals(409,ex.getHttpStatus().value());verifyNoInteractions(sessions);}
    @Test void apiKeyCannotAdministerPersonalLoginSessions(){assertThrows(CustomException.class,()->controller.list(user(),request));assertThrows(CustomException.class,()->controller.logoutAll(user(),request));verifyNoInteractions(sessions);}
    @Test void targetedRevokeAlwaysPassesCurrentUserOwnership(){authenticated();UUID target=UUID.randomUUID();controller.revoke(target,user(),request);verify(sessions).revoke(target,3L);}
    @Test void allLogoutRevokesCurrentActorOnly(){authenticated();controller.logoutAll(user(),request);verify(sessions).revokeAll(3L);}
    @Test void sessionListHasBoundedContractAndLegacyIndicator(){when(request.getHeader("Authorization")).thenReturn("Bearer legacy");when(jwt.resolveToken(request)).thenReturn("legacy");when(sessions.list(3L,null)).thenReturn(List.of());var list=controller.list(user(),request);assertTrue(list.legacySession());assertEquals(100,list.limit());assertTrue(list.sessions().isEmpty());}
}
