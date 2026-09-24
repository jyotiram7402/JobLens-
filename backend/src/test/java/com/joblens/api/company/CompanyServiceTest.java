package com.joblens.api.company;

import com.joblens.api.company.domain.Company;
import com.joblens.api.company.dto.CompanyResponse;
import com.joblens.api.company.dto.CreateCompanyRequest;
import com.joblens.api.company.dto.UpdateCompanyRequest;
import com.joblens.api.company.exception.CompanyAlreadyExistsException;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.company.exception.IllegalCompanyNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service behaviour with the repository mocked: no Spring context and no
 * database, so these run in milliseconds and can cover the awkward cases
 * cheaply.
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    private static CreateCompanyRequest createRequest(String name) {
        return new CreateCompanyRequest(name, "  ", "https://example.com", null, null,
                "Information Technology", "Pune, India");
    }

    private static Company company(String name, String slug) {
        return Company.create(name, slug, null, null, null, null, null, null);
    }

    @Test
    void createsCompanyWithNormalizedNameAndSlug() {
        when(companyRepository.findByNormalizedName("tata consultancy services"))
                .thenReturn(Optional.empty());
        when(companyRepository.existsBySlug(any())).thenReturn(false);
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyResponse response =
                companyService.create(createRequest("  Tata  Consultancy Services "));

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).saveAndFlush(saved.capture());

        assertThat(saved.getValue().getNormalizedName()).isEqualTo("tata consultancy services");
        assertThat(saved.getValue().getSlug()).isEqualTo("tata-consultancy-services");
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.slug()).isEqualTo("tata-consultancy-services");
    }

    @Test
    void storesBlankOptionalFieldsAsNullRatherThanEmptyStrings() {
        when(companyRepository.findByNormalizedName(any())).thenReturn(Optional.empty());
        when(companyRepository.existsBySlug(any())).thenReturn(false);
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        companyService.create(createRequest("Acme"));

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDescription()).isNull();
    }

    @Test
    void rejectsDuplicateDifferingOnlyByCasingOrSpacing() {
        when(companyRepository.findByNormalizedName("tata consultancy services"))
                .thenReturn(Optional.of(
                        company("Tata Consultancy Services", "tata-consultancy-services")));

        assertThatThrownBy(() ->
                companyService.create(createRequest("TATA   CONSULTANCY   SERVICES")))
                .isInstanceOf(CompanyAlreadyExistsException.class)
                .hasMessageContaining("tata-consultancy-services");

        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNameThatNormalizesToNothing() {
        assertThatThrownBy(() -> companyService.create(createRequest("!!!")))
                .isInstanceOf(IllegalCompanyNameException.class);

        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getById(id))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void throwsNotFoundForUnknownSlug() {
        when(companyRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getBySlug("nope"))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void renamingRecomputesNormalizedNameButKeepsTheSlug() {
        UUID id = UUID.randomUUID();
        Company existing = company("Old Name", "old-name");
        when(companyRepository.findById(id)).thenReturn(Optional.of(existing));
        when(companyRepository.findByNormalizedName("new name")).thenReturn(Optional.empty());
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        companyService.update(id,
                new UpdateCompanyRequest("New Name", null, null, null, null, null, null));

        assertThat(existing.getNormalizedName()).isEqualTo("new name");
        assertThat(existing.getSlug()).isEqualTo("old-name");
    }

    @Test
    void rejectsRenameOntoAnotherCompany() {
        UUID id = UUID.randomUUID();
        when(companyRepository.findById(id)).thenReturn(Optional.of(company("Old Name", "old-name")));
        when(companyRepository.findByNormalizedName("taken name"))
                .thenReturn(Optional.of(company("Taken Name", "taken-name")));

        assertThatThrownBy(() -> companyService.update(id,
                new UpdateCompanyRequest("Taken Name", null, null, null, null, null, null)))
                .isInstanceOf(CompanyAlreadyExistsException.class);
    }
}
