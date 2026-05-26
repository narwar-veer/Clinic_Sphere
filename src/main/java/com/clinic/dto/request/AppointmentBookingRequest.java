package com.clinic.dto.request;

import com.clinic.entity.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentBookingRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^\\+91[0-9]{10}$", message = "Phone must be in +91XXXXXXXXXX format")
    private String phone;

    @NotNull
    @Min(1)
    @Max(120)
    private Integer age;

    @NotNull
    private Gender gender;

    private String symptoms;

    @NotNull
    private Long slotId;
}
