package com.nathan.tibiastats.application.mediator;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediatorTest {
    @Test
    void sendsRegisteredCommandToHandler() {
        Mediator mediator = new Mediator();
        AtomicReference<TestCommand> handled = new AtomicReference<>();
        mediator.register(TestCommand.class, handled::set);
        TestCommand command = new TestCommand("update");

        mediator.send(command);

        assertThat(handled).hasValue(command);
    }

    @Test
    void throwsWhenNoHandlerIsRegisteredForCommandType() {
        Mediator mediator = new Mediator();
        TestCommand command = new TestCommand("missing");

        assertThatThrownBy(() -> mediator.send(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TestCommand.class.getName());
    }

    private record TestCommand(String name) implements Command {}
}
