package org.arquillian.site.model;

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

@DataMapping(value = "releases", parentArray = true)
public record Releases(List<Release> list) {

    public record Release(
            String version,
            String title,
            String date,
            URL url,
            @JsonProperty("change_log")
            URL changeLog
    ) {
        public Date releaseDate() throws ParseException {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

            return formatter.parse(date);
        }

        public String majorVersion() {
            return version.split("\\.")[0];
        }
    }
}
