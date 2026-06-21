package com.prishtha.mvp.studio.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WritingCategoryId implements Serializable {

    @Column(name = "writing_id")
    private Long writingId;

    @Column(name = "category_id")
    private Long categoryId;
}
