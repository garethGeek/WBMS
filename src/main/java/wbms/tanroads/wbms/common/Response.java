package wbms.tanroads.wbms.common;

import lombok.Data;

@Data
public class Response<T> {
    private Boolean status;

    private Integer code;

    private String description;

    private T data;
}
