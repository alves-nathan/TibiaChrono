package com.nathan.tibiastats.application.mediator;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Mediator {
    private final Map<Class<?>, CommandHandler<?>> handlers = new ConcurrentHashMap<>();
    public <C extends Command> void register(Class<C> type, CommandHandler<C> handler){ handlers.put(type, handler); }
    @SuppressWarnings("unchecked")
    public <C extends Command> void send(C command){
        CommandHandler<C> h = (CommandHandler<C>) handlers.get(command.getClass());
        if(h==null) throw new IllegalStateException("No handler for "+command.getClass());
        h.handle(command);
    }
}