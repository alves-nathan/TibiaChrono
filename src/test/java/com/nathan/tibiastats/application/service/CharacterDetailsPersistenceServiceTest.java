package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterDetailsPersistenceServiceTest {
    @Test
    void saveCharacterDetailsUpdatesChangedFieldsAndClearsPreviousError() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNamingService namingService = mock(CharacterNamingService.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        CharacterDetailsPersistenceService service = new CharacterDetailsPersistenceService(
                repository,
                namingService,
                transactionTemplate
        );
        CharacterEntity character = character(10L);
        character.setSex(CharacterEntity.Sex.female);
        character.setLevel(50);
        character.setAchievementPoints(1);
        character.setResidence("Carlin");
        character.setAccStatus("Free Account");
        character.setDetailsLastScrapeError("previous error");
        Vocation currentVocation = vocation(1, "Knight");
        character.setVocation(currentVocation);
        Vocation scrapedVocation = vocation(2, "Elite Knight");
        Instant attemptedAt = Instant.parse("2026-06-02T10:15:30Z");
        OffsetDateTime lastLogin = OffsetDateTime.parse("2026-06-01T08:00:00Z");
        Instant creationDate = Instant.parse("2020-01-01T00:00:00Z");
        CharacterDetailPort.CharacterDetails details = new CharacterDetailPort.CharacterDetails(
                " Official Name ",
                List.of("Former Name"),
                CharacterEntity.Sex.male,
                " Elite Knight ",
                300,
                100,
                " Thais ",
                lastLogin,
                " Premium Account ",
                creationDate,
                "Antica"
        );
        when(repository.findById(10L)).thenReturn(Optional.of(character));
        when(namingService.reconcileOfficialNames(character, "Official Name", List.of("Former Name")))
                .thenReturn(character);
        when(repository.findVocationByNameOrPromotionName("Elite Knight")).thenReturn(Optional.of(scrapedVocation));
        when(repository.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterDetailsPersistenceService.SaveResult result = service.saveCharacterDetails(
                10L,
                "Requested Name",
                details,
                attemptedAt
        );

        assertThat(result.changed()).isTrue();
        assertThat(result.status()).isEqualTo("UPDATED");
        assertThat(character.getSex()).isEqualTo(CharacterEntity.Sex.male);
        assertThat(character.getLevel()).isEqualTo(300);
        assertThat(character.getVocation()).isSameAs(scrapedVocation);
        assertThat(character.getAchievementPoints()).isEqualTo(100);
        assertThat(character.getResidence()).isEqualTo("Thais");
        assertThat(character.getLastLogin()).isEqualTo(lastLogin);
        assertThat(character.getAccStatus()).isEqualTo("Premium Account");
        assertThat(character.getCreationDate()).isEqualTo(creationDate);
        assertThat(character.getDetailsLastScrapedAt()).isEqualTo(attemptedAt);
        assertThat(character.getDetailsLastScrapeStatus()).isEqualTo("UPDATED");
        assertThat(character.getDetailsLastScrapeError()).isNull();
        verify(repository).save(character);
    }

    @Test
    void saveCharacterDetailsUsesRequestedNameWhenCurrentNameIsBlankAndReportsUnchanged() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNamingService namingService = mock(CharacterNamingService.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        CharacterDetailsPersistenceService service = new CharacterDetailsPersistenceService(
                repository,
                namingService,
                transactionTemplate
        );
        CharacterEntity character = character(20L);
        Vocation vocation = vocation(3, "Royal Paladin");
        character.setVocation(vocation);
        character.setLevel(400);
        Instant attemptedAt = Instant.parse("2026-06-02T10:15:30Z");
        CharacterDetailPort.CharacterDetails details = new CharacterDetailPort.CharacterDetails(
                " ",
                List.of(),
                null,
                "Royal Paladin",
                400,
                null,
                " ",
                null,
                " ",
                null,
                null
        );
        when(repository.findById(20L)).thenReturn(Optional.of(character));
        when(namingService.reconcileOfficialNames(character, "Requested Name", List.of()))
                .thenReturn(character);
        when(repository.findVocationByNameOrPromotionName("Royal Paladin")).thenReturn(Optional.of(vocation));
        when(repository.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterDetailsPersistenceService.SaveResult result = service.saveCharacterDetails(
                20L,
                "Requested Name",
                details,
                attemptedAt
        );

        assertThat(result.changed()).isFalse();
        assertThat(result.status()).isEqualTo("UNCHANGED");
        assertThat(character.getDetailsLastScrapedAt()).isEqualTo(attemptedAt);
        assertThat(character.getDetailsLastScrapeStatus()).isEqualTo("UNCHANGED");
    }

    @Test
    void saveCharacterDetailsReportsMissingLocalCharacterWithoutSaving() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNamingService namingService = mock(CharacterNamingService.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        CharacterDetailsPersistenceService service = new CharacterDetailsPersistenceService(
                repository,
                namingService,
                transactionTemplate
        );
        CharacterDetailPort.CharacterDetails details = new CharacterDetailPort.CharacterDetails(
                "Missing",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(repository.findById(99L)).thenReturn(Optional.empty());

        CharacterDetailsPersistenceService.SaveResult result = service.saveCharacterDetails(
                99L,
                "Missing",
                details,
                Instant.parse("2026-06-02T10:15:30Z")
        );

        assertThat(result.changed()).isFalse();
        assertThat(result.status()).isEqualTo("MISSING_LOCAL_CHARACTER");
        verify(repository, never()).save(any(CharacterEntity.class));
    }

    @Test
    void markAttemptDelegatesInsideTransaction() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNamingService namingService = mock(CharacterNamingService.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        CharacterDetailsPersistenceService service = new CharacterDetailsPersistenceService(
                repository,
                namingService,
                transactionTemplate
        );
        Instant attemptedAt = Instant.parse("2026-06-02T10:15:30Z");

        service.markAttempt(42L, attemptedAt, "FAILED", "boom");

        verify(repository).markDetailsScrapeAttempt(42L, attemptedAt, "FAILED", "boom");
    }

    private CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return character;
    }

    private Vocation vocation(Integer id, String name) {
        Vocation vocation = new Vocation();
        vocation.setId(id);
        vocation.setName(name);
        return vocation;
    }

    private TransactionTemplate immediateTransactionTemplate() {
        return new ImmediateTransactionTemplate();
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }

        @Override
        public void executeWithoutResult(Consumer<TransactionStatus> action) {
            action.accept(null);
        }
    }
}
