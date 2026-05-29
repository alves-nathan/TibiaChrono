package com.nathan.tibiastats.application.mediator;
public interface CommandHandler<C extends Command> {
    void handle(C command);
}