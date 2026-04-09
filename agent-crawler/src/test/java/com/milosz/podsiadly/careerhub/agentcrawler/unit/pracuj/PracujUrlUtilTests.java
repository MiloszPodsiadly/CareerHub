package com.milosz.podsiadly.careerhub.agentcrawler.unit.pracuj;

import com.milosz.podsiadly.careerhub.agentcrawler.pracuj.PracujUrlUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PracujUrlUtilTests {

    @Test
    void should_extract_offer_id_from_plain_and_encoded_offer_urls() {
        assertThat(PracujUrlUtil.extractOfferId("https://it.pracuj.pl/praca/java-dev,oferta,1001")).isEqualTo("1001");
        assertThat(PracujUrlUtil.extractOfferId("https://it.pracuj.pl/praca/java-dev%2Coferta%2C2002")).isEqualTo("2002");
    }

    @Test
    void should_convert_relative_href_to_absolute_url() {
        assertThat(PracujUrlUtil.toAbs("/praca/java-dev,oferta,1001")).isEqualTo("https://it.pracuj.pl/praca/java-dev,oferta,1001");
        assertThat(PracujUrlUtil.toAbs("praca/java-dev,oferta,1001")).isEqualTo("https://it.pracuj.pl/praca/java-dev,oferta,1001");
    }

    @Test
    void should_normalize_url_by_removing_query_trailing_slash_and_encoding_commas() {
        String result = PracujUrlUtil.normalize(" https://it.pracuj.pl/praca/java-dev,oferta,1001/?a=1 ");

        assertThat(result).isEqualTo("https://it.pracuj.pl/praca/java-dev%2Coferta%2C1001");
    }
}
