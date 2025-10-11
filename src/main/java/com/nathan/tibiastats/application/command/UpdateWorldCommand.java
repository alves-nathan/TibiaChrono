package com.nathan.tibiastats.application.command;
import com.nathan.tibiastats.application.mediator.Command;
public record UpdateWorldCommand(String worldName) implements Command {}