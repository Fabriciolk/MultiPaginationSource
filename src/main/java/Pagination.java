import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class Pagination {

    private int page;

    private int pageSize;
}
