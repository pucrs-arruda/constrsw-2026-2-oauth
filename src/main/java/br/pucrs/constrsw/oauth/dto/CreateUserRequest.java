package br.pucrs.constrsw.oauth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(
        @NotBlank(message = "Username é obrigatório")
        @Pattern(
                regexp = "^([!-#-'*+/-9=?A-Z\\^-~]+(\\.[!-#-'*+/-9=?A-Z\\^-~]+)*|\"([!#-\\[\\^-~ \\t]|(\\\\[\\t -~]))+\")@([!-#-'*+/-9=?A-Z\\^-~]+(\\.[!-#-'*+/-9=?A-Z\\^-~]+)*|\\[[\\t -Z\\^-~]*\\])$",
                message = "E-mail inválido - RFC 5322 official standard regular expression to validate email addresses"
        )
        @Schema(example = "teste.swagger@pucrs.br")
        String username,

        @Pattern(
                regexp = "^([!-#-'*+/-9=?A-Z\\^-~]+(\\.[!-#-'*+/-9=?A-Z\\^-~]+)*|\"([!#-\\[\\^-~ \\t]|(\\\\[\\t -~]))+\")@([!-#-'*+/-9=?A-Z\\^-~]+(\\.[!-#-'*+/-9=?A-Z\\^-~]+)*|\\[[\\t -Z\\^-~]*\\])$",
                message = "E-mail inválido - RFC 5322 official standard regular expression to validate email addresses"
        )
        @Schema(example = "teste.swagger@pucrs.br")
        String email,

        @JsonProperty("first-name")
        @JsonAlias({"first_name", "firstName"})
        String firstName,

        @JsonProperty("last-name")
        @JsonAlias({"last_name", "lastName"})
        String lastName,

        Boolean enabled,

        @NotBlank(message = "Password é obrigatório")
        String password
) {
    public CreateUserRequest {
        if (email == null || email.isBlank()) {
            email = username;
        }
        if (enabled == null) {
            enabled = true;
        }
    }
}
