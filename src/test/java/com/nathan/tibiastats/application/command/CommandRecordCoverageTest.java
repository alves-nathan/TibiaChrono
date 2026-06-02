package com.nathan.tibiastats.application.command;

import com.nathan.tibiastats.application.mediator.Command;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRecordCoverageTest {
    @Test
    void updateCommandsImplementCommandAndExposeRecordState() {
        UpdateAllWorldsCommand allWorlds = new UpdateAllWorldsCommand();
        UpdateHighscoresCommand highscores = new UpdateHighscoresCommand();
        UpdateWorldCommand world = new UpdateWorldCommand("Antica");

        assertThat(allWorlds).isInstanceOf(Command.class);
        assertThat(highscores).isInstanceOf(Command.class);
        assertThat(world).isInstanceOf(Command.class);
        assertThat(world.worldName()).isEqualTo("Antica");
        assertThat(new UpdateAllWorldsCommand()).isEqualTo(allWorlds);
        assertThat(new UpdateHighscoresCommand()).isEqualTo(highscores);
        assertThat(new UpdateWorldCommand("Antica")).isEqualTo(world);
        assertThat(world.toString()).contains("Antica");
    }
}
