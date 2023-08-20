import java.util.ArrayList;
import java.util.List;

public class MultiPaginationSource<T> implements PaginationSource<T> {

    private final List<PaginationSource<T>> orderedPaginationSourceList;
    private final List<Long> orderedCountList = new ArrayList<>();
    private final List<Long> cumulativeOrderedCountList = new ArrayList<>();
    private Long totalCount = 0L;
    private boolean countDataAlreadyExtracted = false;

    public MultiPaginationSource(List<PaginationSource<T>> orderedPaginationSourceList) {
        this.orderedPaginationSourceList = orderedPaginationSourceList;
    }

    @Override
    public List<T> getItemsList(int page, int pageSize) throws IncompatibleCountFromSourceException {
        extractCountDataFromSources();
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
                        String errorMsg = "Pagination from source '" + orderedPaginationSourceList.get(i).getName() + "' " +
                                "is not enabled and list's size returned (" + itemList.size() + ") " +
                                "is not equals its count (" + orderedCountList.get(i) + ").";

                        throw new IncompatibleCountFromSourceException(errorMsg);
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
}
