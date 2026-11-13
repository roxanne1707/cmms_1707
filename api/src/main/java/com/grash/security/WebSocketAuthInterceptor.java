package com.grash.security;

import com.grash.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    // Only live sockets, removed on disconnect. Never log raw tokens.
    private final Map<String,String> socketTokens = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(@NonNull Message<?> message,@NonNull MessageChannel channel) {
        StompHeaderAccessor accessor=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
        if(accessor==null || accessor.getCommand()==null) return message;
        String socketId=accessor.getSessionId();
        if(accessor.getCommand()==StompCommand.DISCONNECT) {
            if(socketId!=null) socketTokens.remove(socketId);
            return message;
        }
        String token=accessor.getCommand()==StompCommand.CONNECT ? accessor.getFirstNativeHeader("token") :
                socketId==null ? null : socketTokens.get(socketId);
        if(token==null) denied();
        Authentication auth=jwtTokenProvider.getAuthentication(token);
        accessor.setUser(auth);
        if(accessor.getCommand()==StompCommand.CONNECT && socketId!=null) socketTokens.put(socketId,token);
        if(accessor.getCommand()==StompCommand.SUBSCRIBE && !allowedSubscription(accessor.getDestination(),auth.getName())) denied();
        // This product exposes server notifications/import/export progress, not client-side broker publishing.
        if(accessor.getCommand()==StompCommand.SEND) denied();
        return message;
    }

    boolean allowedSubscription(String destination,String username) {
        if(destination==null || username==null) return false;
        String own="/user/"+username+"/";
        String suffix=destination.startsWith(own) ? destination.substring(own.length()) :
                destination.startsWith("/user/") ? destination.substring(6) : "";
        return suffix.equals("notifications") || suffix.matches("(?:imports|exports)/[a-fA-F0-9-]{36}");
    }

    public ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(@NonNull Message<?> message,@NonNull MessageChannel channel) {
                if(SimpMessageHeaderAccessor.getMessageType(message.getHeaders())!=SimpMessageType.MESSAGE) return message;
                String socketId=SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
                String token=socketId==null?null:socketTokens.get(socketId);
                if(token==null) return null;
                try { jwtTokenProvider.getAuthentication(token);return message; }
                catch(RuntimeException ex) { socketTokens.remove(socketId);return null; }
            }
        };
    }

    @EventListener public void disconnected(SessionDisconnectEvent event) { socketTokens.remove(event.getSessionId()); }
    private static void denied() { throw new CustomException("WebSocket access denied",HttpStatus.FORBIDDEN); }
}
