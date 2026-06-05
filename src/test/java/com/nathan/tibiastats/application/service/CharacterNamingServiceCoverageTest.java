package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterNamingServiceCoverageTest {

    @Test
    void ensureCharacterForNameDelegatesToObservedResolverWhenFormerNamesAreEmpty() {
        CharacterNameParser parser = mock(CharacterNameParser.class);
        CharacterObservedNameResolver observedResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityReconciliationService reconciliation = mock(CharacterIdentityReconciliationService.class);
        CharacterNamingService service = new CharacterNamingService(parser, observedResolver, reconciliation);
        CharacterEntity observed = new CharacterEntity();
        when(parser.parseFormerNames(" ", "Current Name")).thenReturn(List.of());
        when(observedResolver.resolveObservedName("Current Name")).thenReturn(observed);

        assertThat(service.ensureCharacterForName("Current Name", " ")).isSameAs(observed);

        verify(observedResolver).resolveObservedName("Current Name");
    }

    @Test
    void ensureCharacterForNameDelegatesToOfficialReconciliationWhenFormerNamesExist() {
        CharacterNameParser parser = mock(CharacterNameParser.class);
        CharacterObservedNameResolver observedResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityReconciliationService reconciliation = mock(CharacterIdentityReconciliationService.class);
        CharacterNamingService service = new CharacterNamingService(parser, observedResolver, reconciliation);
        CharacterEntity canonical = new CharacterEntity();
        List<String> formerNames = List.of("Old Name");
        when(parser.parseFormerNames("Old Name", "Current Name")).thenReturn(formerNames);
        when(reconciliation.reconcileOfficialNames("Current Name", formerNames)).thenReturn(canonical);

        assertThat(service.ensureCharacterForName("Current Name", "Old Name")).isSameAs(canonical);

        verify(reconciliation).reconcileOfficialNames("Current Name", formerNames);
    }

    @Test
    void directEntryPointsDelegateToCollaborators() {
        CharacterNameParser parser = mock(CharacterNameParser.class);
        CharacterObservedNameResolver observedResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityReconciliationService reconciliation = mock(CharacterIdentityReconciliationService.class);
        CharacterNamingService service = new CharacterNamingService(parser, observedResolver, reconciliation);
        CharacterEntity known = new CharacterEntity();
        CharacterEntity canonical = new CharacterEntity();
        List<String> formerNames = List.of("Old Name");
        when(observedResolver.resolveObservedName("Plain Name")).thenReturn(known);
        when(reconciliation.reconcileOfficialNames(known, "Current Name", formerNames)).thenReturn(canonical);

        assertThat(service.resolveObservedName("Plain Name")).isSameAs(known);
        assertThat(service.reconcileOfficialNames(known, "Current Name", formerNames)).isSameAs(canonical);

        verify(observedResolver).resolveObservedName("Plain Name");
        verify(reconciliation).reconcileOfficialNames(known, "Current Name", formerNames);
    }

    @Test
    void handleRenamedUsesOldNameWhenPresentAndEmptyFormerNamesWhenAbsent() {
        CharacterNameParser parser = mock(CharacterNameParser.class);
        CharacterObservedNameResolver observedResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityReconciliationService reconciliation = mock(CharacterIdentityReconciliationService.class);
        CharacterNamingService service = new CharacterNamingService(parser, observedResolver, reconciliation);
        CharacterEntity character = new CharacterEntity();
        CharacterName oldName = CharacterName.createActive("Old Name", character);

        service.handleRenamed(character, "New Name", oldName);
        service.handleRenamed(character, "Another Name", null);

        verify(reconciliation).reconcileOfficialNames(character, "New Name", List.of("Old Name"));
        verify(reconciliation).reconcileOfficialNames(character, "Another Name", List.of());
    }
}
