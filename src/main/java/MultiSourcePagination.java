import java.util.ArrayList;
import java.util.List;

public class MultiSourcePagination<T> implements SourcePagination<T> {

    private final List<SourcePagination<T>> orderedSourcePaginationList;
    private final List<Long> orderedCountList = new ArrayList<>();

    private final List<Long> cumulativeOrderedCountList = new ArrayList<>();
    private Long totalCount = 0L;
    private boolean countDataAlreadyExtracted = false;

    public MultiSourcePagination(List<SourcePagination<T>> orderedSourcePaginationList) {
        this.orderedSourcePaginationList = orderedSourcePaginationList;
    }

    @Override
    public List<T> getItemsList(int page, int pageSize) throws IncompatibleCountFromSourceException {
        if (!countDataAlreadyExtracted) extractCountDataFromSources();
        List<T> itemList = new ArrayList<>();
        int countToIgnore = 0;
        int relativePage = page;

        for (int i = 0; i < orderedSourcePaginationList.size(); i++) {
            if ((long) (page - 1) * pageSize < cumulativeOrderedCountList.get(i)) {
                Pagination relativePagination = Pagination.builder()
                        .page(relativePage)
                        .pageSize(pageSize)
                        .build();

                int countFirstRelativePage = pageSize - (countToIgnore % pageSize == 0 ? pageSize : countToIgnore % pageSize);
                Pagination bestSizedAdaptedPagination = getBestSizeAdaptedPagination(relativePagination, countFirstRelativePage);

                itemList = orderedSourcePaginationList.get(i).getItemsList(bestSizedAdaptedPagination.getPage(), bestSizedAdaptedPagination.getPageSize());

                if (orderedSourcePaginationList.get(i).isPaginationEnabled()) {
                    itemList = itemList.subList((countFirstRelativePage + (relativePage - 2) * pageSize) % bestSizedAdaptedPagination.getPageSize(), Math.min(countFirstRelativePage + pageSize, itemList.size()));
                } else {
                    if (itemList.size() != orderedCountList.get(i)) {
                        String errorMsg = "Pagination from source '" + orderedSourcePaginationList.get(i).getName() + "' " +
                                "is not enabled and list's size returned (" + itemList.size() + ") " +
                                "is not equals its count (" + orderedCountList.get(i) + ").";

                        throw new IncompatibleCountFromSourceException(errorMsg);
                    }
                    itemList = itemList.subList((page - 1) * pageSize, Math.min((page - 1) * pageSize + pageSize, itemList.size()));
                }

                tryToAddRemainingPaginationData(itemList, i, pageSize);

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
        if (countDataAlreadyExtracted) return totalCount;
        else extractCountDataFromSources();

        return totalCount;
    }

    @Override
    public String getName() {
        StringBuilder stringBuilder = new StringBuilder("[" + orderedSourcePaginationList.get(0).getName());

        for (int i = 1; i < orderedSourcePaginationList.size(); i++)
            stringBuilder.append(" + ").append(orderedSourcePaginationList.get(i).getName());

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

        if (relativePagination.getPageSize() > countBeforeInterestData) {
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

    private void tryToAddRemainingPaginationData(List<T> itemList, int lastSourceIndex, int pageSize) throws IncompatibleCountFromSourceException {
        for (int j = lastSourceIndex + 1; j < orderedSourcePaginationList.size(); j++) {
            if (itemList.size() < pageSize) {
                List<T> contratosChamada2 = orderedSourcePaginationList.get(j).getItemsList(1, pageSize - itemList.size());
                System.out.println(contratosChamada2);
                itemList.addAll(contratosChamada2.subList(0, Math.min(pageSize - itemList.size(), contratosChamada2.size())));
            } else break;
        }
    }

    private void extractCountDataFromSources() {
        orderedSourcePaginationList.forEach(src -> {
            Long currentCount = src.getCount();

            totalCount += currentCount;
            orderedCountList.add(currentCount);
            cumulativeOrderedCountList.add(totalCount);
        });

        countDataAlreadyExtracted = true;
    }
}
