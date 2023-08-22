import java.util.*;

public class MultiPaginationSource<T> implements PaginationSource<T> {

    private List<PaginationSource<T>> orderedPaginationSourceList;
    private List<Long> orderedCountList = new ArrayList<>();

    private List<Long> cumulativeOrderedCountList = new ArrayList<>();
    private Long totalCount = 0L;
    private boolean countDataAlreadyExtracted = false;
    private boolean sourcesAlreadySortedAndMerged = false;
    private Map<Comparator<T>, SortConfigIndexes> sortConfig = null;

    public MultiPaginationSource(List<PaginationSource<T>> orderedPaginationSourceList, Map<Comparator<T>, SortConfigIndexes> sortConfig) {
        this.orderedPaginationSourceList = orderedPaginationSourceList;
        this.sortConfig = sortConfig;
    }

    public MultiPaginationSource(List<PaginationSource<T>> orderedPaginationSourceList) {
        this.orderedPaginationSourceList = orderedPaginationSourceList;
    }

    @Override
    public List<T> getItemsList(int page, int pageSize) throws IncompatibleCountFromSourceException {
        extractCountDataFromSources();

        if (sortConfig != null && !sortConfig.isEmpty() && !sourcesAlreadySortedAndMerged) {
            tryToSortAndMergePaginationSource(page, pageSize);
        }

        List<T> itemList = new ArrayList<>();
        int countToIgnore = 0;
        int relativePage = page;

        for (int i = 0; i < orderedPaginationSourceList.size(); i++) {
            if ((long) (page - 1) * pageSize < cumulativeOrderedCountList.get(i)) {
                Pagination relativePagination = Pagination.builder()
                        .page(relativePage)
                        .pageSize(pageSize)
                        .build();

                int countFirstRelativePage = pageSize - (countToIgnore % pageSize);
                Pagination bestSizedAdaptedPagination = getBestSizeAdaptedPagination(relativePagination, countFirstRelativePage);

                itemList = orderedPaginationSourceList.get(i).getItemsList(bestSizedAdaptedPagination.getPage(), bestSizedAdaptedPagination.getPageSize());

                if (orderedPaginationSourceList.get(i).isPaginationEnabled()) {
                    itemList = itemList.subList(
                            (countFirstRelativePage + (relativePage - 2) * pageSize) % bestSizedAdaptedPagination.getPageSize(),
                            Math.min(countFirstRelativePage + pageSize, itemList.size())
                    );
                } else {
                    if (itemList.size() != orderedCountList.get(i)) {
                        throw new IncompatibleCountFromSourceException(orderedPaginationSourceList.get(i).getName(), (long) itemList.size(), orderedCountList.get(i));
                    }

                    int indexToStart = relativePage == 1 ? 0 : countFirstRelativePage + ((relativePage - 2) * pageSize);

                    itemList = itemList.subList(
                            indexToStart,
                            Math.min(indexToStart + pageSize, itemList.size())
                    );
                }

                tryToAddRemainingDataFromNextSources(itemList, i, pageSize);

                break;
            } else {
                countToIgnore += orderedCountList.get(i);
                relativePage = page - (countToIgnore / pageSize);
            }
        }

        return itemList;
    }

    private void tryToSortAndMergePaginationSource(int page, int pageSize) throws IncompatibleCountFromSourceException {
        int countStartToExtract = (page - 1) * pageSize;
        int countFinalToExtract = (int) Math.min((long) page * pageSize - 1, totalCount);

        int firstSourceIndexNeeded = 0;
        int lastSourceIndexNeeded = 0;

        for (int i = 0; i < cumulativeOrderedCountList.size(); i++) {
            int startCountOnSource = i == 0 ? 0 : (int) cumulativeOrderedCountList.get(i).longValue();
            int finalCountOnSource = (int) cumulativeOrderedCountList.get(i).longValue() - 1;

            if (startCountOnSource <= countStartToExtract && countStartToExtract <= finalCountOnSource) {
                firstSourceIndexNeeded = i;

                for (int j = i; j < cumulativeOrderedCountList.size(); j++) {
                    startCountOnSource = j == 0 ? 0 : (int) cumulativeOrderedCountList.get(j).longValue();
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
            for (Map.Entry<Comparator<T>, SortConfigIndexes> entry : sortConfig.entrySet()) {
                if ((entry.getValue().getStartIndex() <= i && i <= entry.getValue().getFinalIndex()) &&
                        ((firstSourceIndexNeeded <= entry.getValue().getFinalIndex() && entry.getValue().getFinalIndex() <= lastSourceIndexNeeded) ||
                                (firstSourceIndexNeeded <= entry.getValue().getStartIndex() && entry.getValue().getStartIndex() <= lastSourceIndexNeeded)))
                {
                    PaginationSource<T> mergedSource = mergeSource(entry.getValue(), entry.getKey());
                    newOrderedPaginationSourceList.add(mergedSource);
                    for (int j = entry.getValue().getStartIndex(); j <= entry.getValue().getFinalIndex(); j++) indexesToIgnore.add(j);
                    sortConfig.remove(entry.getKey());
                    break;
                } else if (!indexesToIgnore.contains(i)) {
                    newOrderedPaginationSourceList.add(orderedPaginationSourceList.get(i));
                }
            }
        }

        resetCountData();
        orderedPaginationSourceList = newOrderedPaginationSourceList;
        extractCountDataFromSources();
        sourcesAlreadySortedAndMerged = true;
    }

    private PaginationSource<T> mergeSource(SortConfigIndexes indexes, Comparator<T> sorter) throws IncompatibleCountFromSourceException {
        StringBuilder newName = new StringBuilder("sorted[");
        List<T> mergedItemList = new ArrayList<>();

        for (int i = indexes.getStartIndex(); i <= indexes.getFinalIndex(); i++) {
            mergedItemList.addAll(orderedPaginationSourceList.get(i).getItemsList(
                    1,
                    (int) orderedPaginationSourceList.get(i).getCount().longValue()
            ));
            newName.append(orderedPaginationSourceList.get(i).getName());

            if (i + 1 <= indexes.getFinalIndex()) newName.append(" + ");
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

            @Override
            public boolean isPaginationEnabled() {
                return false;
            }
        };
    }

    @Override
    public Long getCount() {
        extractCountDataFromSources();
        return totalCount;
    }

    @Override
    public String getName() {
        StringBuilder stringBuilder = new StringBuilder("[" + orderedPaginationSourceList.get(0).getName());

        for (int i = 1; i < orderedPaginationSourceList.size(); i++)
            stringBuilder.append(" + ").append(orderedPaginationSourceList.get(i).getName());

        stringBuilder.append("]");

        return stringBuilder.toString();
    }

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

        for (int newCandidatePageSize = relativePagination.getPageSize(); newCandidatePageSize < countBeforeInterestData; newCandidatePageSize++) {
            if (newCandidatePageSize - countBeforeInterestData % newCandidatePageSize >= relativePagination.getPageSize()) {
                bestPagination.setPageSize(newCandidatePageSize);
                bestPagination.setPage((int) Math.floor((1.0 * countBeforeInterestData) / newCandidatePageSize) + 1);
                break;
            }
        }

        return bestPagination;
    }

    private void tryToAddRemainingDataFromNextSources(List<T> itemList, int lastSourceIndex, int pageSize) throws IncompatibleCountFromSourceException {
        for (int j = lastSourceIndex + 1; j < orderedPaginationSourceList.size(); j++) {
            if (itemList.size() < pageSize) {
                List<T> itemsFromNextSource = orderedPaginationSourceList.get(j).getItemsList(1, pageSize - itemList.size());
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
}
