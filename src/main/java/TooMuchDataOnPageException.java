public class TooMuchDataOnPageException extends Exception{

    public TooMuchDataOnPageException(int sizeFound, int sizeExpected, int page, String sourceName) {
        super("Excected find a maximum of " + sizeExpected + " items, but found " + sizeFound + " after request page " + page + " to source " + sourceName);
    }
}
