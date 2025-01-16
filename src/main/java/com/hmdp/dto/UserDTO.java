package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
