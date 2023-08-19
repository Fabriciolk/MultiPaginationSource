import java.util.List;

public interface SourcePagination<T>
{
    List<T> getItemsList(int page, int pageSize) throws IncompatibleCountFromSourceException;

    Long getCount();

    default Long getPageCount(int pageSize) {
        return (long) Math.ceil((double) getCount() / pageSize);
    }

    // If this method returns false, it means that
    // getItemsList method always will return all
    // data from source.
    default boolean isPaginationEnabled() {
        return true;
    }

    String getName();
}
