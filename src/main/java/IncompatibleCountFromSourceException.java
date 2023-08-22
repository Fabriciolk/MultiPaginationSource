public class IncompatibleCountFromSourceException extends Exception {

    public IncompatibleCountFromSourceException(String sourceName, Long listSize, Long count) {
        super("Pagination from source '" + sourceName + "' " +
                "is not enabled and list's size returned (" + listSize + ") " +
                "is not equals its count (" + count + ").");
    }
}
