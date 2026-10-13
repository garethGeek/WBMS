package wbms.tanroads.wbms.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListResponse <T>{
    private boolean status;
    private int code;
    private Long totalElements;
    private String description;
    private List<T> data;
}
