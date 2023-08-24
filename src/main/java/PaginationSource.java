import java.util.List;

public interface PaginationSource<T>
{
    List<T> getItemsList(int page, int pageSize) throws TooMuchDataOnPageException;

    Long getCount();

    default Long getPageAmount(int pageSize) {
        return (long) Math.ceil((double) getCount() / pageSize);
    }

    String getName();
}
