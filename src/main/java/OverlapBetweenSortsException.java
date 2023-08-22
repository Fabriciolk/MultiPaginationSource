public class OverlapBetweenSortsException extends Exception {

    public OverlapBetweenSortsException() {
        super("Can not exist overlap between sort configution indexes. " +
                "It means that a source can't be sort using more than one comparator");
    }
}
