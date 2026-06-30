package io.github.posseidon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilityTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
    @CsvSource({
            "Invoice,                invoice",
            "Utility Bill,           utility_bill",
            "'  bank-statement\n',   bank_statement",
            "TAX RETURN (2024),      tax_return_2024",
            "Employment Contract,    employment_contract",
            "bank_statement,         bank_statement",
            "INVOICE,                invoice",
    })
    void normalizesToLowercaseSnakeCase(String input, String expected) {
        assertThat(StringUtility.normalize(input)).isEqualTo(expected.strip());
    }

    @Test
    void nullInputReturnsEmpty() {
        assertThat(StringUtility.normalize(null)).isEmpty();
    }

    @Test
    void blankInputReturnsEmpty() {
        assertThat(StringUtility.normalize("   ")).isEmpty();
    }

    @Test
    void onlyFirstLineIsUsed() {
        assertThat(StringUtility.normalize("invoice\nThis is an invoice document."))
                .isEqualTo("invoice");
    }

    @Test
    void consecutiveUnderscoresCollapsed() {
        assertThat(StringUtility.normalize("bank  -  statement")).isEqualTo("bank_statement");
    }

    @Test
    void leadingAndTrailingUnderscoresStripped() {
        assertThat(StringUtility.normalize("(invoice)")).isEqualTo("invoice");
    }

    @Test
    void digitsArePreserved() {
        assertThat(StringUtility.normalize("form 1099")).isEqualTo("form_1099");
    }
}
