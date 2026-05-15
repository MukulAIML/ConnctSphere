package com.connectsphere.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "hashtags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HashtagDocument {

    @Id
    private String id;   // ES string id = hashtagId.toString()

    @Field(type = FieldType.Keyword)
    private String tag;

    @Field(type = FieldType.Integer)
    private Integer postCount;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUsedAt;
}
