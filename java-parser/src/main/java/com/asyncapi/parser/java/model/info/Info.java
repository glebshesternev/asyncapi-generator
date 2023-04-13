package com.asyncapi.parser.java.model.info;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.asyncapi.parser.java.ExtendableObject;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Info extends ExtendableObject {

    @NotNull
    private String title;

    @NotNull
    private String version;

    @Nullable
    private String description;

    @Nullable
    private String termsOfService;

    @Nullable
    private Contact contact;

    @Nullable
    private License license;

}
