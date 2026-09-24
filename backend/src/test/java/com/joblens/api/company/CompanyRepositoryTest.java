package com.joblens.api.company;

import com.joblens.api.company.domain.Company;
import com.joblens.api.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence behaviour against real PostgreSQL: the mapping, the unique
 * constraints and the search query. None of this can be verified with mocks --
 * a unique index either exists in the migration or it does not.
 */
@RepositoryTest
class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    private static Company company(String name, String slug) {
        return Company.create(name, slug, "A description", "https://example.com",
                "https://example.com/careers", null, "Information Technology", "Pune, India");
    }

    @BeforeEach
    void seed() {
        companyRepository.saveAndFlush(company("Tata Consultancy Services",
                "tata-consultancy-services"));
        companyRepository.saveAndFlush(company("Infosys Limited", "infosys-limited"));
    }

    @Test
    void assignsIdAndAuditTimestampsOnSave() {
        Company saved = companyRepository.saveAndFlush(company("Acme Corporation", "acme-corporation"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsBySlug() {
        assertThat(companyRepository.findBySlug("infosys-limited"))
                .get()
                .extracting(Company::getName)
                .isEqualTo("Infosys Limited");
    }

    @Test
    void findsByNormalizedName() {
        assertThat(companyRepository.findByNormalizedName("tata consultancy services")).isPresent();
    }

    @Test
    void reportsWhetherSlugIsTaken() {
        assertThat(companyRepository.existsBySlug("infosys-limited")).isTrue();
        assertThat(companyRepository.existsBySlug("nobody-has-this")).isFalse();
    }

    @Test
    void rejectsSecondCompanyWithTheSameNormalizedName() {
        // Different capitalisation and spacing, so a different `name` but the
        // same normalized_name. The unique index is what must stop this.
        Company duplicate = company("TATA   CONSULTANCY   SERVICES", "tata-consultancy-services-2");

        assertThatThrownBy(() -> companyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondCompanyWithTheSameSlug() {
        Company duplicate = company("Completely Different Name", "infosys-limited");

        assertThatThrownBy(() -> companyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchMatchesOnNormalizedNameFragment() {
        Page<Company> results = companyRepository.search("tata", pageOfTen());

        assertThat(results.getContent())
                .extracting(Company::getName)
                .containsExactly("Tata Consultancy Services");
    }

    @Test
    void searchWithNullTermReturnsEverythingActive() {
        Page<Company> results = companyRepository.search(null, pageOfTen());

        assertThat(results.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchExcludesInactiveCompanies() {
        Company inactive = company("Dormant Holdings", "dormant-holdings");
        inactive.deactivate();
        companyRepository.saveAndFlush(inactive);

        Page<Company> results = companyRepository.search("dormant", pageOfTen());

        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void searchPagesInTheDatabaseRatherThanInMemory() {
        Page<Company> firstPage = companyRepository.search(null, PageRequest.of(0, 1,
                Sort.by(Sort.Direction.ASC, "name")));

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    private static PageRequest pageOfTen() {
        return PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name"));
    }
}
