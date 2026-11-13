package com.grash.security;

import com.grash.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTest {
    final JwtTokenProvider jwt=mock(JwtTokenProvider.class);
    final WebSocketAuthInterceptor interceptor=new WebSocketAuthInterceptor(jwt);
    final MessageChannel channel=mock(MessageChannel.class);
    final Authentication auth=mock(Authentication.class);
    Message<?> frame(StompCommand command,String token,String destination) {
        StompHeaderAccessor h=StompHeaderAccessor.create(command);h.setLeaveMutable(true);h.setSessionId("socket-1");
        if(token!=null)h.setNativeHeader("token",token);if(destination!=null)h.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
    }
    void connect() {when(jwt.getAuthentication("jwt")).thenReturn(auth);when(auth.getName()).thenReturn("owner@example.invalid");
        interceptor.preSend(frame(StompCommand.CONNECT,"jwt",null),channel);}
    @Test void anonymousConnectFailsClosed() {assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.CONNECT,null,null),channel));}
    @Test void anonymousSubscribeCannotSupplyTokenOutsideConnect() {assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.SUBSCRIBE,"jwt","/user/notifications"),channel));}
    @Test void ownSubscriptionAllowedAndReauthenticated() {connect();Message<?> m=frame(StompCommand.SUBSCRIBE,null,"/user/owner@example.invalid/notifications");assertSame(m,interceptor.preSend(m,channel));verify(jwt,times(2)).getAuthentication("jwt");}
    @Test void anotherUsersDestinationIsDenied() {connect();assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.SUBSCRIBE,null,"/user/other@example.invalid/notifications"),channel));}
    @Test void directBrokerSubscriptionsAndClientPublishingDenied() {connect();assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.SUBSCRIBE,null,"/notifications-user123"),channel));assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.SEND,null,"/notifications"),channel));}
    @Test void revokedSocketCannotReceiveFurtherMessages() {connect();SimpMessageHeaderAccessor h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);h.setSessionId("socket-1");Message<?> m=MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());assertSame(m,interceptor.outbound().preSend(m,channel));when(jwt.getAuthentication("jwt")).thenThrow(new CustomException("revoked",HttpStatus.UNAUTHORIZED));assertNull(interceptor.outbound().preSend(m,channel));}
    @Test void disconnectForgetsCredentials() {connect();interceptor.preSend(frame(StompCommand.DISCONNECT,null,null),channel);assertThrows(CustomException.class,()->interceptor.preSend(frame(StompCommand.SUBSCRIBE,null,"/user/notifications"),channel));}
    @Test void scopedImportAndExportSubscriptionsRemainCompatible() {assertTrue(interceptor.allowedSubscription("/user/owner@example.invalid/imports/11111111-1111-1111-1111-111111111111","owner@example.invalid"));assertTrue(interceptor.allowedSubscription("/user/exports/11111111-1111-1111-1111-111111111111","owner@example.invalid"));assertFalse(interceptor.allowedSubscription("/user/other@example.invalid/exports/11111111-1111-1111-1111-111111111111","owner@example.invalid"));}
}
