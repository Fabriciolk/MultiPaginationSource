# MultiPaginationSource
Let's imagine that you have, let's say, 4 tables (A, B, C, D) in your database (relational) and for some reason, they carry the same type of data, that is, have same colums and it's semantic is same too. How can a service extract pagination from these 4 sources in a way that on point of view of a client, there is only a big one source (table) and he/she only inputs a desired page number and a page size? This is what the code from this repository treats.

We can visualize this problem on image below.

![MultiSourcePagination](https://github.com/Fabriciolk/MultiSourcePagination/assets/72703544/b8986580-d557-4d5a-aab2-f8223639128f)

If the client request page 2 for example, the service should return finals data from first source and some initials data from second source, respecting page size choosed.
