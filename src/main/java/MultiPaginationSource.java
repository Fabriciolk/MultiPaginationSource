import java.util.*;

public class MultiPaginationSource<T> implements PaginationSource<T> {

    private List<PaginationSource<T>> orderedPaginationSourceList;
    private final List<Long> orderedCountList = new ArrayList<>();
    private final List<Long> cumulativeOrderedCountList = new ArrayList<>();
    private Long totalCount = 0L;
    private boolean countDataAlreadyExtracted = false;
    private boolean sourcesAlreadySortedAndMerged = false;
    private Map<Comparator<T>, SortConfigIndexes> sortConfig = null;

    public MultiPaginationSource(List<PaginationSource<T>> orderedPaginationSourceList, Map<Comparator<T>, SortConfigIndexes> sortConfig) throws OverlapBetweenSortsException {
        this.orderedPaginationSourceList = orderedPaginationSourceList;
        this.sortConfig = sortConfig;

        validateSortConfigIndexes(sortConfig);
    }

    public MultiPaginationSource(List<PaginationSource<T>> orderedPaginationSourceList) {
        this.orderedPaginationSourceList = orderedPaginationSourceList;
    }

    /**
     *
     *  This method returns interested data accessing sources only
     *  if is needed, based on total count from each one.
     *
     * **/

    @Override
    public List<T> getItemsList(int innerPage, int innerPageSize) throws TooMuchDataOnPageException {
        if (innerPageSize < 0) throw new IndexOutOfBoundsException("innerPageSize = " + innerPageSize);
        if (innerPage <= 0) throw new IndexOutOfBoundsException("innerPage = " + innerPage);
        if (innerPageSize == 0) return new ArrayList<>();

        extractCountDataFromSources();

        if (sortConfig != null && !sortConfig.isEmpty() && !sourcesAlreadySortedAndMerged) {
            tryToSortAndMergePaginationSource(innerPage, innerPageSize);
        }

        List<T> itemList = new ArrayList<>();
        int countToIgnore = 0;
        int relativePage = innerPage;

        for (int i = 0; i < orderedPaginationSourceList.size(); i++) {
            if ((long) (innerPage - 1) * innerPageSize < cumulativeOrderedCountList.get(i)) {
                Pagination relativePagination = Pagination.builder()
                        .page(relativePage)
                        .pageSize(innerPageSize)
                        .build();

                int countFirstRelativePage = innerPageSize - (countToIgnore % innerPageSize);
                Pagination bestSizedAdaptedPagination = getBestSizeAdaptedPagination(relativePagination, countFirstRelativePage);

                itemList = orderedPaginationSourceList.get(i).getItemsList(bestSizedAdaptedPagination.getPage(), bestSizedAdaptedPagination.getPageSize());

                if (itemList.size() > bestSizedAdaptedPagination.getPageSize()) {
                    throw new TooMuchDataOnPageException(itemList.size(), bestSizedAdaptedPagination.getPageSize(), bestSizedAdaptedPagination.getPage(), orderedPaginationSourceList.get(i).getName());
                }

                itemList = filterOnlyInterestedData(innerPageSize, itemList, countFirstRelativePage, relativePage, bestSizedAdaptedPagination);

                tryToAddRemainingDataFromNextSources(itemList, i, innerPageSize);

                break;
            } else {
                countToIgnore += orderedCountList.get(i);
                relativePage = innerPage - (countToIgnore / innerPageSize);
            }
        }

        return itemList;
    }

    @Override
    public Long getCount() {
        extractCountDataFromSources();
        return totalCount;
    }

    @Override
    public Long getPageAmount(int pageSize) {
        return Math.min(1, (long) Math.ceil((double) getCount() / pageSize));
    }

    @Override
    public String getName() {
        StringBuilder stringBuilder = new StringBuilder("[" + orderedPaginationSourceList.get(0).getName());

        for (int i = 1; i < orderedPaginationSourceList.size(); i++)
            stringBuilder.append(" + ").append(orderedPaginationSourceList.get(i).getName());

        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    private List<T> filterOnlyInterestedData(int pageSize, List<T> itemList, int countFirstRelativePage, int relativePage, Pagination bestSizedAdaptedPagination) {
        if (itemList.isEmpty()) return new ArrayList<>();

        return itemList.subList(
                (countFirstRelativePage + (relativePage - 2) * pageSize) % bestSizedAdaptedPagination.getPageSize(),
                Math.min(countFirstRelativePage + pageSize, itemList.size())
        );
    }

    /**
     *
     *  Check if need to merge-and-sort sources or not, depending
     *  on the page and pageSize requested.
     *
     * **/

    private void tryToSortAndMergePaginationSource(int innerPage, int innerPageSize) throws TooMuchDataOnPageException {
        int countStartToExtract = (innerPage - 1) * innerPageSize;
        int countFinalToExtract = (int) Math.min((long) innerPage * innerPageSize - 1, totalCount);

        int firstSourceIndexNeeded = 0;
        int lastSourceIndexNeeded = 0;

        for (int i = 0; i < cumulativeOrderedCountList.size(); i++) {
            int startCountOnSource = i == 0 ? 0 : (int) cumulativeOrderedCountList.get(i - 1).longValue();
            int finalCountOnSource = (int) cumulativeOrderedCountList.get(i).longValue() - 1;

            if (startCountOnSource <= countStartToExtract && countStartToExtract <= finalCountOnSource) {
                firstSourceIndexNeeded = i;

                for (int j = i; j < cumulativeOrderedCountList.size(); j++) {
                    startCountOnSource = j == 0 ? 0 : (int) cumulativeOrderedCountList.get(j - 1).longValue();
                    finalCountOnSource = (int) cumulativeOrderedCountList.get(j).longValue() - 1;

                    if (startCountOnSource <= countFinalToExtract && countFinalToExtract <= finalCountOnSource) {
                        lastSourceIndexNeeded = j;
                        break;
                    }
                }

                break;
            }
        }

        List<PaginationSource<T>> newOrderedPaginationSourceList = new ArrayList<>();
        Set<Integer> indexesToIgnore = new HashSet<>();

        for (int i = 0; i < orderedPaginationSourceList.size(); i++) {
            boolean isTreated = false;

            for (Map.Entry<Comparator<T>, SortConfigIndexes> entry : sortConfig.entrySet()) {
                if ((entry.getValue().getStartIndex() <= i && i <= entry.getValue().getFinalIndex()) &&
                        ((firstSourceIndexNeeded <= entry.getValue().getFinalIndex() && entry.getValue().getFinalIndex() <= lastSourceIndexNeeded) ||
                                (firstSourceIndexNeeded <= entry.getValue().getStartIndex() && entry.getValue().getStartIndex() <= lastSourceIndexNeeded)))
                {
                    PaginationSource<T> mergedSource = mergeSources(entry.getValue(), entry.getKey());
                    newOrderedPaginationSourceList.add(mergedSource);
                    for (int j = entry.getValue().getStartIndex(); j <= entry.getValue().getFinalIndex(); j++) indexesToIgnore.add(j);
                    sortConfig.remove(entry.getKey());
                    break;
                } else if (!indexesToIgnore.contains(i)) {
                    newOrderedPaginationSourceList.add(orderedPaginationSourceList.get(i));
                    indexesToIgnore.add(i);
                }
                isTreated = true;
            }

            if (!isTreated && !indexesToIgnore.contains(i)) {
                newOrderedPaginationSourceList.add(orderedPaginationSourceList.get(i));
                indexesToIgnore.add(i);
            }
        }

        resetCountData();
        orderedPaginationSourceList = newOrderedPaginationSourceList;
        extractCountDataFromSources();
        sourcesAlreadySortedAndMerged = true;
    }

    /**
     *
     *  If we have for example 3 sources with 5, 3 and 7 items in each one,
     *  respectively, we should have, logically, a big one table like this:
     *
     *                          AAAAA/BBBCC/CCCCC
     *
     *  If this method indexes (minimum, maximum) as (1, 2), this method will
     *  merge the second and third sources, resulting a big one table like this:
     *
     *                          AAAAA/BBBBB/BBBBB
     *
     *  If necessary, the method sort all those 10 items too.
     *
     * **/

    private PaginationSource<T> mergeSources(SortConfigIndexes indexes, Comparator<T> sorter) throws TooMuchDataOnPageException {
        StringBuilder newName = new StringBuilder("sorted[");
        List<T> mergedItemList = new ArrayList<>();

        for (int i = indexes.getStartIndex(); i <= indexes.getFinalIndex(); i++) {
            mergedItemList.addAll(orderedPaginationSourceList.get(i).getItemsList(
                    1,
                    (int) orderedPaginationSourceList.get(i).getCount().longValue()
            ));
            newName.append(orderedPaginationSourceList.get(i).getName());

            if (i + 1 <= indexes.getFinalIndex()) newName.append(" + ");
            else newName.append("]");
        }

        mergedItemList.sort(sorter);

        return new PaginationSource<T>() {
            @Override
            public List<T> getItemsList(int page, int pageSize) {
                return mergedItemList;
            }

            @Override
            public Long getCount() {
                return (long) mergedItemList.size();
            }

            @Override
            public String getName() {
                return newName.toString();
            }
        };
    }

    /**
     *
     *  Imagine we have 3 sources with 5, 3 and 17 items in each one,
     *  respectively. Suppose too page is 4 and pageSize is 5, requested
     *  by client (who called getItemsList method). Logically, we wil
     *  have a big one table like this:
     *
     *                              (to client)
     *                                  v
     *              AAAAA|BBBCC|CCCCC|CCCCC|CCCCC
     *
     *  where A's represents items from first source, B's from second,
     *  and C's from third source. As requested by client, it needs to
     *  receive a list containing CCCCC from the third source.
     *
     *  The main problem is: Since we know interested data is in the third
     *  source and this source (and all others) returns data based on
     *  pagination, which page and pageSize we should request to third
     *  source to extract AT LEAST the interest data and, if need to
     *  extract non-interested data, extract MINIMUM AS POSSIBLE
     *  non-interested data? This method calculates the minimum pageSize
     *  need to achive it.
     *
     *  In this case, relativePage is 3 and countFirstPage is 2.
     *
     * **/

    private Pagination getBestSizeAdaptedPagination(Pagination relativePagination, int countFirstPage) {
        Pagination bestPagination = Pagination.builder()
                .page(relativePagination.getPage())
                .pageSize(relativePagination.getPageSize())
                .build();

        if (countFirstPage == 0) return bestPagination;

        int countBeforeInterestData = countFirstPage + (relativePagination.getPage() - 2) * relativePagination.getPageSize();

        if (countBeforeInterestData < relativePagination.getPageSize()) {
            return Pagination.builder()
                    .page(1)
                    .pageSize(relativePagination.getPageSize() + countBeforeInterestData)
                    .build();
        }

        for (int newCandidatePageSize = relativePagination.getPageSize(); newCandidatePageSize <= countBeforeInterestData; newCandidatePageSize++) {
            if (newCandidatePageSize - countBeforeInterestData % newCandidatePageSize >= relativePagination.getPageSize()) {
                bestPagination.setPageSize(newCandidatePageSize);
                bestPagination.setPage((int) Math.floor((1.0 * countBeforeInterestData) / newCandidatePageSize) + 1);
                break;
            }
        }

        return bestPagination;
    }

    /**
     *
     *  If pageSize is 10, for example, and we have 3 items in itemList,
     *  this method try to add 7 more items from next sources that was
     *  not accessed yet.
     *
     * **/

    private void tryToAddRemainingDataFromNextSources(List<T> itemList, int lastSourceIndex, int pageSize) throws TooMuchDataOnPageException {
        for (int j = lastSourceIndex + 1; j < orderedPaginationSourceList.size(); j++) {
            if (itemList.size() < pageSize) {
                List<T> itemsFromNextSource = orderedPaginationSourceList.get(j).getItemsList(1, pageSize - itemList.size());
                if (itemsFromNextSource.size() > pageSize) {
                    throw new TooMuchDataOnPageException(itemsFromNextSource.size(), pageSize, 1, orderedPaginationSourceList.get(j).getName());
                }
                itemList.addAll(itemsFromNextSource.subList(0, Math.min(pageSize - itemList.size(), itemsFromNextSource.size())));
            } else break;
        }
    }

    private void extractCountDataFromSources() {
        if (countDataAlreadyExtracted) return;

        orderedPaginationSourceList.forEach(src -> {
            Long currentCount = src.getCount();

            totalCount += currentCount;
            orderedCountList.add(currentCount);
            cumulativeOrderedCountList.add(totalCount);
        });

        countDataAlreadyExtracted = true;
    }

    private void resetCountData() {
        totalCount = 0L;
        orderedCountList.clear();
        cumulativeOrderedCountList.clear();
        countDataAlreadyExtracted = false;
    }

    private void validateSortConfigIndexes(Map<Comparator<T>, SortConfigIndexes> sortConfig) throws OverlapBetweenSortsException {
        for (Map.Entry<Comparator<T>, SortConfigIndexes> entry : sortConfig.entrySet()) {
            if (entry.getValue().getFinalIndex() >= orderedPaginationSourceList.size()) throw new IndexOutOfBoundsException("Some index is out of sources bounds");

            for (Map.Entry<Comparator<T>, SortConfigIndexes> entry2 : sortConfig.entrySet()) {
                if (entry.getValue().equals(entry2.getValue())) continue;

                if ((entry2.getValue().getStartIndex() <= entry.getValue().getFinalIndex() && entry.getValue().getFinalIndex() <= entry2.getValue().getFinalIndex()) ||
                        (entry2.getValue().getStartIndex() <= entry.getValue().getStartIndex() && entry.getValue().getStartIndex() <= entry2.getValue().getFinalIndex())) {
                    throw new OverlapBetweenSortsException();
                }
            }
        }
    }
}
