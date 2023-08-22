import lombok.Getter;

public class SortConfigIndexes {

    @Getter
    private final int startIndex;
    @Getter
    private final int finalIndex;

    public SortConfigIndexes(int startIndex, int finalIndex) throws Exception {
        if (startIndex > finalIndex) throw new Exception();

        this.startIndex = startIndex;
        this.finalIndex = finalIndex;
    }

}
