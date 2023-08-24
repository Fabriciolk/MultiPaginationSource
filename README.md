# MultiPaginationSource
Let's imagine that you have, let's say, 4 tables (A, B, C, D) in your database (relational) and for some reason, they carry the same type of data, that is, have same colums and it's semantics allow a merge between them. How can a service extract pagination from these 4 sources in a way that on point of view of a client, there is only a big one source (table) and he/she only inputs a desired page number and a page size? This is what the code from this repository treats.

We can visualize this problem on image below.

![MultiSourcePagination](https://github.com/Fabriciolk/MultiSourcePagination/assets/72703544/b8986580-d557-4d5a-aab2-f8223639128f)

If the client request page 2 for example, the service should return finals data from first source and some initials data from second source, respecting page size choosed.

Furthermore, the feature supports sort combinations too. Using our example, if for some reason, the data from B e C should be merged and sorted before be requested, the service provide another constructor where you can add a map with key-value as Comparator-Indexes, where Indexes in this case should be 1, 2 (See SortConfigIndexes.java). If this sort configuration is used, but a client request some page whose data is not in B or C, the service will not sort, avoiding unnecessary work. Other important point is the sort config doesn't accept overlaid indexes. It means that you can't add a key-value AnyComparator-[1,2] and AnyComparator-[2,3], because index 2 can not have two sort (some improvement later, including a choose of order of them). 

We can visualize the merge-sort on image below, using a key-value IntegerComparator-[1,3]

![MultiSourcePagination-Sort](https://github.com/Fabriciolk/MultiPaginationSource/assets/72703544/dd3885bc-8b52-4086-8c1e-5fe8eaa54cab)


